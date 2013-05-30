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
package org.kevoree.library.apachehttpd;


public class Test {





    public static void main(String[] args) throws Exception {

        NativeExecManager t = new NativeExecManager();
        t.setNameNativeExec("apache2224");

        t.install_generics();
        t.install_lib("libapr-0.so.0");
        t.install_lib("libaprutil-0.so.0");
        t.install_lib("libexpat.so.0");





    }


}
