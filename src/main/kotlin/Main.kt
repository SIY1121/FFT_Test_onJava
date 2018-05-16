import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage

class Main  : Application() {
    override fun start(primaryStage: Stage) {
        primaryStage.scene = Scene(FXMLLoader.load<Parent>(ClassLoader.getSystemResource("main.fxml")),500.0,500.0)
        primaryStage.setOnCloseRequest { System.exit(0) }
        primaryStage.show()
    }

    companion object {
        @JvmStatic
        fun main(args : Array<String>){
            Application.launch(Main::class.java, *args)
        }
    }
}