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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.*
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

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
        val grabber = FFmpegFrameGrabber("C:\\Users\\sota\\OneDrive - 筑波大学\\StudioOneProjects\\2018-05-13 市川 創大\\Mixdown\\Mixdown(2).flac")
        grabber.sampleMode = FrameGrabber.SampleMode.FLOAT
        grabber.start()

        val cap = ((grabber.lengthInTime / 1000.0 / 1000.0) * grabber.sampleRate * grabber.audioChannels).toInt()

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
        println("done fft")



        canvas.graphicsContext2D.stroke = Color.BLUE

        for (i in 0 until array.size / 2) {
            val x = canvas.width * i.toDouble() / (array.size / 2.0) * 20.0
            val y = Math.sqrt(Math.pow(array[i].toDouble(), 2.0) + Math.pow(array[i].toDouble(), 2.0)) / 100.0
            val hz = i * grabber.sampleRate / (array.size / 2.0)
            canvas.graphicsContext2D.strokeLine(x, canvas.height - y, x, canvas.height)
        }

        FloatFFT_1D(array.size.toLong()).realInverse(array,true)
        println("done rfft")

        val audioFormat = AudioFormat((grabber.sampleRate.toFloat()), 16, 2, true, false)

        val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
        val audioLine = AudioSystem.getLine(info) as SourceDataLine
        audioLine.open(audioFormat)
        audioLine.start()


        val s = array.mapIndexed { index, value -> ((value / window[index])*Short.MAX_VALUE).toShort() }.toShortArray()
        println("conv short")
        val bb = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        println("alloc ${bb.limit()}")
        bb.asShortBuffer().put(s)
        println("put")
        while(bb.remaining()>0){
            val d = ByteArray(88200)
            bb.get(d,0,Math.min(bb.remaining(),88200))

            audioLine.write(d,0,d.size)
        }
        println("done")


    }

    fun genHammingWindow(size: Int): FloatArray {
        val res = FloatArray(size)

        for (i in 0 until size)
            res[i] = (0.54 - 0.46 * Math.cos(2 * Math.PI * (i / size.toDouble()))).toFloat()

        return res
    }
}