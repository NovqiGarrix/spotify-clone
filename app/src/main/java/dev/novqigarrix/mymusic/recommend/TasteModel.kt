package dev.novqigarrix.mymusic.recommend

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Observation noise of the probit link. */
private const val BETA_SQUARED = 1.0

/** Prior variance of a feature the model has never seen. */
private const val DEFAULT_PRIOR_VARIANCE = 0.15

/**
 * Variance added back to a feature each time it is observed, so certainty never fully collapses
 * and the model keeps adapting when taste drifts.
 */
private const val DRIFT_VARIANCE = 0.01

/** Lowest variance a weight may reach; keeps a little exploration alive for well-known features. */
private const val MIN_VARIANCE = 0.02

/**
 * Scales the spread of Thompson samples. Full-width samples are so noisy on a fresh model that
 * "similar to what was playing" gets lost; half-width still explores but stays on topic.
 */
private const val EXPLORATION = 0.5

/** A weight's belief: a Gaussian with this mean and variance. */
data class Prior(val mean: Double, val variance: Double)

@Serializable
class TasteSnapshot(
    val means: Map<String, Double> = emptyMap(),
    val variances: Map<String, Double> = emptyMap(),
)

/**
 * Online Bayesian probit regression over sparse binary features (the AdPredictor model): every
 * weight is a Gaussian, and each observation updates the weights in closed form in microseconds.
 *
 * Used as a contextual bandit: [sampler] draws one weight vector from the posterior (Thompson
 * sampling), so well-understood songs are usually ranked by what the user likes, while uncertain
 * ones occasionally get a turn. That balance is what keeps recommendations from going stale.
 */
class TasteModel(
    private val priors: (String) -> Prior?,
    snapshot: TasteSnapshot = TasteSnapshot(),
) {
    private val means = HashMap(snapshot.means)
    private val variances = HashMap(snapshot.variances)

    fun snapshot(): TasteSnapshot = TasteSnapshot(HashMap(means), HashMap(variances))

    private fun prior(feature: String): Prior = priors(feature) ?: Prior(0.0, DEFAULT_PRIOR_VARIANCE)
    private fun mean(feature: String): Double = means[feature] ?: prior(feature).mean
    private fun variance(feature: String): Double = variances[feature] ?: prior(feature).variance

    /** Probability that the user enjoys (finishes) a song with these features. */
    fun predict(features: Collection<String>): Double {
        var mu = 0.0
        var sigmaSquared = BETA_SQUARED
        for (f in features) {
            mu += mean(f)
            sigmaSquared += variance(f)
        }
        return normalCdf(mu / sqrt(sigmaSquared))
    }

    /** Learns from one outcome: [liked] true when the song was finished or liked, false when skipped. */
    fun update(features: Collection<String>, liked: Boolean) {
        if (features.isEmpty()) return
        for (f in features) {
            val prior = prior(f)
            variances[f] = (variance(f) + DRIFT_VARIANCE).coerceAtMost(prior.variance)
        }
        val y = if (liked) 1.0 else -1.0
        var mu = 0.0
        var sigmaSquared = BETA_SQUARED
        for (f in features) {
            mu += mean(f)
            sigmaSquared += variance(f)
        }
        val sigma = sqrt(sigmaSquared)
        val t = y * mu / sigma
        val v = vFunction(t)
        val w = v * (v + t)
        for (f in features) {
            val variance = variance(f)
            means[f] = mean(f) + y * variance / sigma * v
            variances[f] = (variance * (1.0 - variance / sigmaSquared * w)).coerceAtLeast(MIN_VARIANCE)
        }
    }

    /**
     * One Thompson sample of the weights, drawn lazily per feature and reused for every song scored
     * with the returned function, so a whole ranking pass sees a single consistent weight vector.
     */
    fun sampler(random: Random = Random.Default): (Collection<String>) -> Double {
        val drawn = HashMap<String, Double>()
        return { features ->
            features.sumOf { f -> drawn.getOrPut(f) { mean(f) + EXPLORATION * sqrt(variance(f)) * random.nextGaussian() } }
        }
    }
}

private fun Random.nextGaussian(): Double {
    // Box–Muller transform.
    val u1 = nextDouble().coerceAtLeast(Double.MIN_VALUE)
    val u2 = nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}

private fun normalPdf(x: Double): Double = exp(-x * x / 2.0) / sqrt(2.0 * PI)

/** Standard normal CDF via the Abramowitz–Stegun 7.1.26 erf approximation (error < 1.5e-7). */
private fun normalCdf(x: Double): Double {
    val z = abs(x) / sqrt(2.0)
    val t = 1.0 / (1.0 + 0.3275911 * z)
    val poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
    val erf = 1.0 - poly * exp(-z * z)
    return if (x >= 0) 0.5 * (1.0 + erf) else 0.5 * (1.0 - erf)
}

/** v(t) = φ(t) / Φ(t), with the asymptote -t used far in the tail where Φ underflows. */
private fun vFunction(t: Double): Double {
    if (t < -6.0) return -t
    return normalPdf(t) / normalCdf(t)
}
