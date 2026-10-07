package dev.novqigarrix.mymusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Constraints
import dev.novqigarrix.mymusic.ui.theme.Motion
import kotlinx.coroutines.launch

/**
 * Spotify's tap feedback: no ripple, the element springs down slightly while pressed.
 * Implemented as a [Modifier.Node] so it carries click semantics and needs no composition state.
 */
fun Modifier.bounceClick(
    onClickLabel: String? = null,
    role: Role = Role.Button,
    pressedScale: Float = Motion.PRESSED_SCALE,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = this then BounceClickElement(onClick, onLongClick, onClickLabel, role, pressedScale)

private data class BounceClickElement(
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)?,
    val onClickLabel: String?,
    val role: Role,
    val pressedScale: Float,
) : ModifierNodeElement<BounceClickNode>() {
    override fun create() = BounceClickNode(onClick, onLongClick, onClickLabel, role, pressedScale)

    override fun update(node: BounceClickNode) =
        node.update(onClick, onLongClick, onClickLabel, role, pressedScale)
}

private class BounceClickNode(
    private var clickAction: () -> Unit,
    private var longClickAction: (() -> Unit)?,
    private var clickLabel: String?,
    private var clickRole: Role,
    private var pressedScale: Float,
) : DelegatingNode(), LayoutModifierNode, SemanticsModifierNode {

    private val scale = Animatable(1f)
    private var hasLongClick = longClickAction != null

    private val pointerInput = delegate(
        SuspendingPointerInputModifierNode {
            detectTapGestures(
                onPress = {
                    val nodeScope = this@BounceClickNode.coroutineScope
                    nodeScope.launch { scale.animateTo(pressedScale, Motion.pressSpring) }
                    tryAwaitRelease()
                    nodeScope.launch { scale.animateTo(1f, Motion.pressSpring) }
                },
                onLongPress = if (hasLongClick) {
                    { longClickAction?.invoke() }
                } else {
                    null
                },
                onTap = { clickAction() },
            )
        },
    )

    fun update(
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?,
        onClickLabel: String?,
        role: Role,
        scale: Float,
    ) {
        clickAction = onClick
        longClickAction = onLongClick
        clickLabel = onClickLabel
        clickRole = role
        pressedScale = scale
        if (hasLongClick != (onLongClick != null)) {
            hasLongClick = onLongClick != null
            pointerInput.resetPointerInputHandler()
        }
        invalidateSemantics()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale.value
                scaleY = scale.value
            }
        }
    }

    override val shouldMergeDescendantSemantics: Boolean get() = true

    override fun SemanticsPropertyReceiver.applySemantics() {
        role = clickRole
        onClick(label = clickLabel) {
            clickAction()
            true
        }
        longClickAction?.let { action ->
            onLongClick {
                action()
                true
            }
        }
    }
}
