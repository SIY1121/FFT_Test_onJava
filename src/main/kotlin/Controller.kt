import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.canvas.Canvas
import javafx.scene.layout.AnchorPane
import org.bytedeco.javacpp.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.FrameGrabber
import org.jtransforms.fft.FloatFFT_1D
import java.io.File
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
        run(
                "D:\\Music\\THE IDOLM@STER CINDERELLA GIRLS MASTER SEASONS AUTUMN!\\03. xwsxwzjtha.mp3",
                "C:\\Users\\sota\\Downloads\\IMreverbs\\On a Star.wav"
        )
    }

    private fun run(srcFile: String, irFile: String) {
        try {
            //インパルス応答を読み込み
            val irSamples = loadIrSamples(irFile)
            //前半半分にパディングを入れる
            val irSamplesWithPadding = FloatArray(irSamples.size) + irSamples

            //ソースファイルを開く
            val grabber = FFmpegFrameGrabber(srcFile)
            grabber.sampleMode = FrameGrabber.SampleMode.FLOAT
            grabber.start()

            val recorder = FFmpegFrameRecorder("out.aac", 2)
            recorder.audioCodec = avcodec.AV_CODEC_ID_AAC
            recorder.sampleRate = 44100
            recorder.audioBitrate = 192_000
            recorder.start()

            //窓関数生成
            val window = genHannWindow(irSamplesWithPadding.size)
            val fft = FloatFFT_1D(irSamplesWithPadding.size.toLong())

            //インパルスに窓関数適用
//            for (i in 0 until irSamplesWithPadding.size)
//                irSamplesWithPadding[i] *= window[i]

            //インパルスをFFTにかける
            fft.realForward(irSamplesWithPadding)

            var prevSamples = FloatArray(irSamplesWithPadding.size / 2)
            while (true) {
                //新しいサンプルを読む
                val samples = readSamples(grabber, irSamplesWithPadding.size / 2) ?: break

                //過去の1ブロックと結合
                val inputSamples = prevSamples + samples

                //窓関数を適用
//                for (i in 0 until inputSamples.size)
//                    inputSamples[i] *= window[i]
                //FFT実行
                fft.realForward(inputSamples)

                //インパルスとソースを周波数領域で乗算
                val dst = multipleComplex(inputSamples, irSamplesWithPadding)
                //逆FFT実行
                fft.realInverse(dst, true)
                //窓関数で戻す
//                for (i in 0 until dst.size)
//                    dst[i] /= window[i]
//                println("max ${dst.max()} min ${dst.min()} ${window.min()}")
                //前半半分を使用
                val buf = FloatBuffer.allocate(dst.size / 2)
                buf.put(dst, 0, dst.size / 2)
                buf.position(0)
                recorder.recordSamples(buf)

                prevSamples = samples
                println(grabber.timestamp.toDouble() / grabber.lengthInTime * 100)
            }
            grabber.stop()
            recorder.stop()

            println("done!")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadIrSamples(irFile: String): FloatArray {
        val grabber = FFmpegFrameGrabber(irFile)
        grabber.sampleMode = FrameGrabber.SampleMode.FLOAT
        grabber.start()

        val samples = FloatArray(((grabber.lengthInTime / 1000_000.0) * grabber.sampleRate * grabber.audioChannels).toInt() + 1)
        var read = 0

        while (true) {
            val buf = grabber.grabSamples()?.samples?.get(0) as? FloatBuffer ?: break
            buf.get(samples, read, buf.limit())
            read += buf.limit()
        }
        grabber.stop()

        return samples
    }

    var tmpBuffer: FloatBuffer? = null
    private fun readSamples(grabber: FFmpegFrameGrabber, size: Int): FloatArray? {
        val result = FloatArray(size)
        var read = 0
        while (read < size) {
            if (tmpBuffer == null || tmpBuffer?.remaining() == 0)
                tmpBuffer = grabber.grabSamples()?.samples?.get(0) as? FloatBuffer ?: break

            val toRead = Math.min(tmpBuffer?.remaining() ?: 0, size - read)
            tmpBuffer?.get(result, read, toRead)
            read += toRead
        }
        return if (read > 0) result else null
    }

    private fun multipleComplex(src1: FloatArray, src2: FloatArray): FloatArray {
        if (src1.size != src2.size) throw Exception("長さの違う配列同士は乗算できません")

        val result = FloatArray(src1.size)

        for (i in 0 until result.size / 2) {
            result[i] = src1[i] * src2[i] - src1[i + 1] * src2[i + 1]
            result[i + 1] = src1[i] * src2[i + 1] + src2[i] * src1[i + 1]
        }


        return result
    }

    private fun genHammingWindow(size: Int): FloatArray {
        val res = FloatArray(size)

        for (i in 0 until size)
            res[i] = (0.54 - 0.46 * Math.cos(2 * Math.PI * (i / size.toDouble()))).toFloat()

        return res
    }

    private fun genHannWindow(size: Int): FloatArray {
        val res = FloatArray(size)

        for (i in 0 until size)
            res[i] = 0.5f - 0.5f * Math.cos(2f * Math.PI * (i.toDouble() / size)).toFloat()

        return res
    }
}