package arcore.sample.app.samplearcoreapp

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import android.widget.TextView
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private var timesTapped: Int = 0
    private var firstAnchor: Anchor? = null
    private var secondAnchor: Anchor? = null
    private var centerAnchor: Anchor? = null
    private val sphereColor = Color(0f, 255f, 0f)
    private val cuboidColor = Color(0f, 255f, 0f)
    private lateinit var sphereMaterial: Material
    private lateinit var cuboidMaterial: Material

    private lateinit var textLabelRenderable: ViewRenderable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initAr()
    }

    private fun initAr() {
        arFragment = uxFragment as ArFragment
        arFragment.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, motionEvent: MotionEvent ->
            onPlaneTapped(hitResult)
        }

        MaterialFactory.makeOpaqueWithColor(this@MainActivity, sphereColor)
            .thenAccept { material -> this.sphereMaterial = material }

        MaterialFactory.makeOpaqueWithColor(this@MainActivity, cuboidColor)
            .thenAccept { material -> this.cuboidMaterial = material }

        prepareTextLabelRenderable()

    }

    private fun onPlaneTapped(hitResult: HitResult) {
        when (timesTapped) {
            0 -> onFirstPointAdded(hitResult)
            1 -> onSecondPointAdded(hitResult)
        }
    }

    private fun onFirstPointAdded(hitResult: HitResult) {
        timesTapped++
        detachExistingAnchors()
        firstAnchor = hitResult.createAnchor()
        val node = putNodeOnAnchor(firstAnchor!!, createSphereRenderable())
        node.worldScale = Vector3(.5f, .5f, .5f)
    }

    private fun createSphereRenderable(): Renderable {
        val renderable: ModelRenderable = ShapeFactory.makeSphere(
            .03f,
            Vector3.zero(),
            sphereMaterial
        )
        renderable.isShadowCaster = false
        return renderable
    }

    private fun onSecondPointAdded(hitResult: HitResult) {
        timesTapped = 0
        secondAnchor = hitResult.createAnchor()
        val node = putNodeOnAnchor(secondAnchor!!, createSphereRenderable())
        node.worldScale = Vector3(.5f, .5f, .5f)

        val lookRotation = getLookRotation(firstAnchor!!, secondAnchor!!)
        drawLine(firstAnchor!!, secondAnchor!!, lookRotation)
        val centerPose = getCenterPose(firstAnchor!!, secondAnchor!!, lookRotation)
        centerAnchor = arFragment.arSceneView.session.createAnchor(centerPose)

        putTextLabel(centerAnchor!!)
    }

    private fun getDistanceInMeters(startPose: Pose, endPose: Pose): Float {
        val dx = subtractWithFluff(startPose.tx(), endPose.tx())
        val dy = subtractWithFluff(startPose.ty(), endPose.ty())
        val dz = subtractWithFluff(startPose.tz(), endPose.tz())
        return Math.sqrt(
            Math.pow(dx.toDouble(), 2.0)
                    + Math.pow(dy.toDouble(), 2.0)
                    + Math.pow(dz.toDouble(), 2.0)
        ).toFloat()
    }

    private fun subtractWithFluff(startValue: Float, endValue: Float): Float {
        return if (startValue == endValue || Math.abs(Math.abs(startValue) - Math.abs(endValue)) < 0.005) {
            0f
        } else {
            startValue - endValue
        }
    }

    private fun getLookRotation(firstAnchor: Anchor, secondAnchor: Anchor): Quaternion {
        val startPosition = Vector3(firstAnchor.pose.tx(), firstAnchor.pose.ty(), firstAnchor.pose.tz())
        val endPosition = Vector3(secondAnchor.pose.tx(), secondAnchor.pose.ty(), secondAnchor.pose.tz())
        val difference = Vector3.subtract(startPosition, endPosition)
        val directionFromTopToBottom = difference.normalized()
        return Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())
    }

    private fun getCenterPose(firstAnchor: Anchor, secondAnchor: Anchor, rotation: Quaternion): Pose {
        val centerX = secondAnchor.pose.tx() + ((firstAnchor.pose.tx() - secondAnchor.pose.tx()) / 2)
        val centerY = secondAnchor.pose.ty() + ((firstAnchor.pose.ty() - secondAnchor.pose.ty()) / 2)
        val centerZ = secondAnchor.pose.tz() + ((firstAnchor.pose.tz() - secondAnchor.pose.tz()) / 2)
        return Pose(
            floatArrayOf(centerX, centerY, centerZ),
            floatArrayOf(rotation.x, rotation.y, rotation.z, rotation.w)
        )
    }

    private fun putTextLabel(anchor: Anchor) {
        (textLabelRenderable.view as TextView).text = String.format("%.2f m", getDistanceInMeters(firstAnchor!!.pose, secondAnchor!!.pose))
        val node = putNodeOnAnchor(anchor, textLabelRenderable)
        val tempLocalLocation = node.localRotation
        tempLocalLocation.set(Vector3(1f, -1f, -1f), 270f)
        node.localRotation = tempLocalLocation
        node.worldScale = Vector3(.7f, .7f, .7f)
    }

    private fun drawLine(startAnchor: Anchor, endAnchor: Anchor, lookRotation: Quaternion) {
        val startPosition = Vector3(startAnchor.pose.tx(), startAnchor.pose.ty(), startAnchor.pose.tz())
        val endPosition = Vector3(endAnchor.pose.tx(), endAnchor.pose.ty(), endAnchor.pose.tz())
        val difference = Vector3.subtract(startPosition, endPosition)

        val cuboidNode = putNodeOnAnchor(endAnchor, createCuboidRenderable(difference.length()))
        cuboidNode.worldPosition = Vector3.add(startPosition, endPosition).scaled(.5f)
        cuboidNode.worldRotation = lookRotation
    }

    private fun createCuboidRenderable(length: Float): Renderable {
        val renderable = ShapeFactory.makeCube(
            Vector3(.006f, .006f, length),
            Vector3.zero(),
            cuboidMaterial
        )
        renderable.isShadowCaster = false
        return renderable
    }

    private fun putNodeOnAnchor(anchor: Anchor, renderable: Renderable) : Node {
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment.arSceneView.scene)
        val node = Node()
        node.renderable = renderable
        node.setParent(anchorNode)
        return node
    }

    private fun prepareTextLabelRenderable() {
        ViewRenderable.builder()
            .setView(this, R.layout.label_view)
            .build()
            .thenAccept { renderable: ViewRenderable ->
                renderable.isShadowCaster = false
                textLabelRenderable = renderable
            }
    }

    private fun detachExistingAnchors() {
        firstAnchor?.detach()
        secondAnchor?.detach()
        centerAnchor?.detach()
    }
}
