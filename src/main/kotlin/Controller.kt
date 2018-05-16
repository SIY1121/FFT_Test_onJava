import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.canvas.Canvas
import javafx.scene.chart.LineChart
import javafx.scene.chart.XYChart
import javafx.scene.layout.AnchorPane
import javafx.scene.paint.Color
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.jtransforms.fft.FloatFFT_1D
import java.net.URL
import java.nio.FloatBuffer
import java.util.*

class Controller : Initializable {
    @FXML
    lateinit var root: AnchorPane
    @FXML
    lateinit var canvas: Canvas

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        root.widthProperty().addListener { _, _, n -> canvas.width = n.toDouble() }
        root.heightProperty().addListener { _, _, n -> canvas.height = n.toDouble() }
    }

    fun onOpenClick(actionEvent: ActionEvent) {
        val grabber = FFmpegFrameGrabber("D:\\Music\\ご注文はうさぎですか？キャラクターソングシリーズ03 マヤ\\WELCOME【う・さ！】（マヤVer.）.flac")
        grabber.sampleMode = FrameGrabber.SampleMode.FLOAT
        grabber.start()

        val cap = ((grabber.lengthInTime / 1000.0 / 1000.0) * grabber.sampleRate * grabber.audioChannels / 2.0).toInt()

        println("alloc $cap")
        val buf = FloatBuffer.allocate(cap)
        //grabber.timestamp = 1000L * 1000L
        var read = 0
        while (read < cap) {
            val b = grabber.grabSamples().samples[0] as FloatBuffer
            if (b.capacity() > cap - read) break
            buf.put(b)

            read += b.capacity()
            println(read)
        }
        println(buf.capacity())
        var array = FloatArray(buf.capacity())
        buf.position(0)
        buf.get(array)
        val window = genHammingWindow(array.size)
        array = array.mapIndexed { index, value -> value * window[index] }.toFloatArray()
        FloatFFT_1D(array.size.toLong()).realForward(array)

        canvas.graphicsContext2D.stroke = Color.BLUE

        for (i in 0 until array.size / 2) {
            val x = canvas.width * i.toDouble() / (array.size / 2.0)
            val y = Math.sqrt(Math.pow(array[i].toDouble(), 2.0) + Math.pow(array[i].toDouble(), 2.0)) / 100.0
            val hz = i * grabber.sampleRate / (array.size / 2.0)
            canvas.graphicsContext2D.strokeLine(x, canvas.height - y, x, canvas.height)
        }

    }

    fun genHammingWindow(size: Int): FloatArray {
        val res = FloatArray(size)

        for (i in 0 until size)
            res[i] = (0.54 - 0.46 * Math.cos(2 * Math.PI * (i / size.toDouble()))).toFloat()

        return res
    }
}