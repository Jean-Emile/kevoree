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
package org.kevoree.framework.port

import org.kevoree.framework.KevoreeChannelFragment
import org.kevoree.framework.KevoreePort
import org.kevoree.framework.message.FragmentBindMessage
import org.kevoree.framework.message.FragmentUnbindMessage

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
trait KevoreeRequiredNonePort: KevoreePort {

    var delegate: KevoreeChannelFragment?
    fun getInOut(): Boolean
    var _isBound: Boolean
    override fun getIsBound(): Boolean {
        return _isBound
    }

    var isPaused: Boolean

    override fun send(o: Any?) {
        delegate?.send(o)
    }

    override fun sendWait(o: Any?): Any? {
        return delegate?.sendWait(o)
    }

    override fun isInPause(): Boolean {
        return isPaused
    }

    override fun processAdminMsg(o: Any): Boolean {
        when(o) {
            is FragmentBindMessage -> {
                bind(o as FragmentBindMessage)
                return true
            }
            is FragmentUnbindMessage -> {
                unbind(o as FragmentUnbindMessage)
                return true
            }
            else -> {
                throw Exception("Bad MSG")
            }

        }
    }

    override fun startPort(tg : ThreadGroup?) {
        isPaused = false
    }

    override fun stop() {
    }

    override fun pause() {
        stop()
        isPaused = true
    }

    override fun forceStop() {
        stop()
    }

    override fun resume() {
        isPaused = false
    }


    private fun bind(bindmsg: FragmentBindMessage) {
        delegate = bindmsg.proxy
        _isBound = true
    }

    private fun unbind(unbindmsg: FragmentUnbindMessage) {
        delegate = null
        _isBound = false
    }

    fun internal_process(msg: Any): Any? {
        if(delegate == null){
            return false
        } else {
            if (getInOut()) {
                try {
                    return delegate!!.send(msg)
                } catch(e: Exception) {
                    return false
                }
            } else {
                try {
                    delegate!!.send(msg)
                } catch (e: Exception){
                    return false
                }
                return false
            }
        }
    }

}
