import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream

fun main() {
    System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
    System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
    System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
    
    try {
        val doc = XWPFDocument()
        val para = doc.createParagraph()
        para.createRun().setText("Hello World")
        val fos = FileOutputStream(File("test.docx"))
        doc.write(fos)
        doc.close()
        fos.close()
        println("Success")
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}
