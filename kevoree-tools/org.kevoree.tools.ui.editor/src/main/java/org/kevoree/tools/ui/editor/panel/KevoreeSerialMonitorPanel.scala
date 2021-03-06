/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, Version 3, 29 June 2007;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, Version 3, 29 June 2007;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kevoree.tools.ui.editor.panel

import org.kevoree.tools.ui.editor.{UIHelper, KevoreeUIKernel}
import org.slf4j.{LoggerFactory, Logger}
import javax.swing._
import text._
import org.kevoree.extra.kserial.{ContentListener, KevoreeSharedCom}
import org.kevoree.extra.kserial.Utils.KHelpers
import org.kevoree.extra.kserial.SerialPort._
import java.awt.{Rectangle, Color, Dimension, BorderLayout}
import java.awt.event.{KeyEvent, KeyAdapter, ActionEvent, ActionListener}

/**
 * Created by jed
 * User: jedartois@gmail.com
 * Date: 05/04/12
 * Time: 11:38
 */

class KevoreeSerialMonitorPanel(kernel: KevoreeUIKernel)  extends JPanel  {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)
  var screen: JTextPane = null
  var toppanel: JPanel = null
  var inputTextField: JTextArea = null
  var send: JButton = null
  var boardPortName  = ""
  var bottomPanel: JPanel = new JPanel
  var speed : Int = 19200

  var device_available = new JComboBox(KHelpers.getPortIdentifiers.toArray)
  var device_baudrates = new JComboBox()

  // close global
  KevoreeSharedCom.killAll()

  KHelpers.getbaudrates().foreach(b =>    {
    UIHelper.addItemToBox(device_baudrates,b.toString)
  }
  )
  device_baudrates.setSelectedItem(speed.toString)


  var serial: SerialPort = new SerialPort(boardPortName, speed)
  logger.info("Ports "+KHelpers.getPortIdentifiers())

  setLayout(new BorderLayout)
  send = new JButton("Send")
  screen = new JTextPane
  screen.setFocusable(true)
  screen.setEditable(true)

  var doc: StyledDocument = screen.getStyledDocument
  var `def`: Style = StyleContext.getDefaultStyleContext.getStyle(StyleContext.DEFAULT_STYLE)
  var incoming: Style = doc.addStyle("incoming", `def`)
  var system: Style = doc.addStyle("system", `def`)
  var outgoing: Style = doc.addStyle("outgoing", `def`)



  StyleConstants.setForeground(system, Color.GRAY)
  StyleConstants.setForeground(incoming, Color.BLUE)
  StyleConstants.setForeground(outgoing, Color.GREEN)

  inputTextField = new JTextArea
  inputTextField.setText("")
  inputTextField.setFocusable(true)
  inputTextField.requestFocus

  toppanel = new JPanel()
  toppanel.setLayout(new BorderLayout)
  toppanel.add(device_available,BorderLayout.CENTER)
  toppanel.add(device_baudrates,BorderLayout.EAST)

  bottomPanel.setLayout(new BorderLayout)
  bottomPanel.add(inputTextField, BorderLayout.CENTER)
  bottomPanel.add(send, BorderLayout.EAST)

  add(new JScrollPane(screen), BorderLayout.CENTER)
  add(bottomPanel, BorderLayout.SOUTH)
  add(toppanel,BorderLayout.NORTH)

  setVisible(true)

  device_available.addActionListener(new ActionListener {
    def actionPerformed(e: ActionEvent) {
      try
      {
        serial.exit()
        boardPortName =     KHelpers.getPortIdentifiers().get(device_available.getSelectedIndex)
        serial.setPort_name(boardPortName)
        serial.setPort_bitrate(speed)
        screen.setText("")
        serial.open
      }catch {
        case se: SerialPortException =>  {
          logger.error(boardPortName+" "+se.toString)
          device_available.remove(device_available.getSelectedIndex)
          //   KHelpers.getPortIdentifiers.toArray.foreach(s =>   device_available.addItem(s)
        }
        case e: Exception =>   logger.error("Fail to open serial port "+boardPortName+" "+e)
      }
    }
  });

  device_baudrates.addActionListener(new ActionListener
  {
    def actionPerformed(e: ActionEvent) {
      try
      {
        serial.exit()
        speed =     KHelpers.getbaudrates()(device_baudrates.getSelectedIndex)
        serial.setPort_name(boardPortName)
        serial.setPort_bitrate(speed)
        screen.setText("")
        serial.open
      }catch {
        case se: SerialPortException =>  {
          logger.error(boardPortName+" "+se.toString)
          device_available.remove(device_available.getSelectedIndex)
          //   KHelpers.getPortIdentifiers.toArray.foreach(s =>   device_available.addItem(s)
        }
        case e: Exception =>   logger.error("Fail to open serial port "+boardPortName+" ",e)
      }
    }
  });

  inputTextField.addKeyListener(new KeyAdapter() {
    override def keyPressed( e:KeyEvent) {
      try
      {
        if (inputTextField.getText.length > 1 && e.getKeyCode ==10)
        {
          serial.write(inputTextField.getText().getBytes())
          appendOutgoing(inputTextField.getText())
          inputTextField.setText("")
        }
      }catch {
        case se: SerialPortException =>   logger.error(boardPortName+" "+se.toString)
        case e: Exception =>   logger.error("Fail to open serial port "+boardPortName+" ",e)
      }

    }

  });

  send.addActionListener(new ActionListener {
    def actionPerformed(e: ActionEvent): Unit = {
      if (inputTextField.getText.length > 1) {
        serial.write(inputTextField.getText().getBytes())
        appendOutgoing(inputTextField.getText())
      }
    }
  })

  def appendOutgoing(text: String): Unit = {
    try {
      var doc: StyledDocument = screen.getStyledDocument
      doc.insertString(doc.getLength, formatForPrint(text), doc.getStyle("outgoing"))
    }
    catch {
      case ex: BadLocationException => {
        logger.error("Error while trying to append local message in the " + this.getName, ex)
      }
    }
  }

  def appendIn(text: String): Unit = {
    try {
      var doc: StyledDocument = screen.getStyledDocument
      val overLength = doc.getLength() + text.length() - 4096
      if (overLength > 0)
      {
        doc.remove(0, overLength);
      }

      doc.insertString(doc.getLength,formatForPrint(text), doc.getStyle("incoming"))
      //scroll the text as it gets more and more
      screen.scrollRectToVisible( new Rectangle(0,screen.getHeight()-2,1,1));
    }
    catch {
      case e : Exception => //ignore
      case ex: BadLocationException => {
        logger.error("Error while trying to append local message in the " + this.getName, ex)
      }
    }
  }

  var mylistener = new SerialPortEventListener(){
    def incomingDataEvent(p1: SerialPortEvent) {
      appendIn(new String(p1.read()))
    }

    def disconnectionEvent(p1: SerialPortDisconnectionEvent) {
      try
      {
        serial.autoReconnect(20, this)
      }
      catch {
        case e: SerialPortException => {
          logger.error("Fail to open serial port "+boardPortName)
          serial.exit()
        }
      }
    }

    def concurrentOpenEvent(p1: SerialConcurrentOpenEvent) {
      logger.warn("Request Concurrent close");
      serial.exit()

    }
  }

  serial.addEventListener(mylistener)

  def close () =
  {
    try
    {
      serial.exit()
      serial.removeEventListener(mylistener)
      mylistener = null
      serial = null
    }
    catch {
      case _ => //ignore
    }
  }

  private def formatForPrint(text: String): String = {
    return (if (text.endsWith("\n")) text else text + "\n")
  }



  if(KHelpers.getPortIdentifiers.size() >0)
  {
    try
    {
      boardPortName =     KHelpers.getPortIdentifiers().get(0)
      serial.setPort_bitrate(speed)
      serial.setPort_name(boardPortName)
      serial.open

    }catch
      {
        case se: SerialPortException =>   logger.error(boardPortName+" ",se.toString)
        case e: Exception =>   logger.error("Fail to open serial port "+boardPortName+" ",e)
      }
  }



}