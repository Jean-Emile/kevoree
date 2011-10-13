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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.kevoree.framework.annotation.processor.visitor;

import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.*;
import com.sun.mirror.util.TypeVisitor;
import org.kevoree.*;
import org.kevoree.framework.annotation.processor.LocalUtility;
import scala.Some;

/**
 *
 * @author ffouquet
 */
public class ServicePortTypeVisitor implements TypeVisitor {

    ServicePortType dataType = KevoreeFactory.eINSTANCE().createServicePortType();

    public ServicePortType getDataType() {
        return dataType;
    }

    public void setDataType(ServicePortType dataType) {
        this.dataType = dataType;
    }

    @Override
    public void visitTypeMirror(TypeMirror t) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void visitPrimitiveType(PrimitiveType t) {
        //dataType.setName(t.getKind().name());
        throw new UnsupportedOperationException("A service port typed can not be a PrimitiveType(" + t.getKind().name() + ")");
    }

    @Override
    public void visitVoidType(VoidType t) {
        //dataType.setName("void");
        throw new UnsupportedOperationException("A service port type can not be void.");
    }

    @Override
    public void visitReferenceType(ReferenceType t) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void visitDeclaredType(DeclaredType t) {
        dataType.setName(t.getDeclaration().getQualifiedName());
    }

    @Override
    public void visitClassType(ClassType t) {

       this.visitTypeDeclaration(t.getDeclaration());
    }

    @Override
    public void visitEnumType(EnumType t) {
        dataType.setName(t.getDeclaration().getQualifiedName());
    }

    @Override
    public void visitInterfaceType(InterfaceType t) {
        this.visitTypeDeclaration(t.getDeclaration());
    }

    @Override
    public void visitAnnotationType(AnnotationType t) {
        dataType.setName(t.getDeclaration().getQualifiedName());
    }

    @Override
    public void visitArrayType(ArrayType t) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void visitTypeVariable(TypeVariable t) {
        dataType.setName(t.getDeclaration().getSimpleName());
    }

    @Override
    public void visitWildcardType(WildcardType t) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void visitTypeDeclaration(TypeDeclaration t) {

        dataType.setName(t.getQualifiedName());

        for (MethodDeclaration m : t.getMethods()) {

            //BUILD NEW OPERATION
            Operation newo = KevoreeFactory.createOperation();
            dataType.addOperations(newo);
            newo.setName(m.getSimpleName());

            //BUILD RETURN TYPE
            DataTypeVisitor rtv = new DataTypeVisitor();
            m.getReturnType().accept(rtv);
            newo.setReturnType(new Some<TypedElement>(LocalUtility.getOraddDataType(rtv.getDataType())));

            //BUILD PARAMETER
            for (ParameterDeclaration p : m.getParameters()) {

                Parameter newp = KevoreeFactory.createParameter();
                newo.addParameters(newp);
                newp.setName(p.getSimpleName());

                DataTypeVisitor ptv = new DataTypeVisitor();
                p.getType().accept(ptv);
                newp.setType(new Some<TypedElement>(LocalUtility.getOraddDataType(ptv.getDataType())));
            }
        }

    }
}
