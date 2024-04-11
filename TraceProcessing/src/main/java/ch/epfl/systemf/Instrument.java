package ch.epfl.systemf;


import org.objectweb.asm.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getMethodType;


public class Instrument {

    public static void main(String[] args) throws IOException {
        ClassReader reader = new ClassReader(new FileInputStream("./Exemple.class"));
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        Instrumenter i = new Instrumenter(ASM9, writer);
        reader.accept(i,0);
        FileOutputStream fos = new FileOutputStream("./Exemple.class");
        fos.write(writer.toByteArray());
        fos.close();
    }

    public static class Instrumenter extends ClassVisitor  {
        public final static String STORE_TYPE;
        public final static String BRANCH_TYPE;

        static {
            try {
                STORE_TYPE = getMethodDescriptor(EventLogger.class.getMethod("store", int.class, int.class));
                BRANCH_TYPE = getMethodDescriptor(EventLogger.class.getMethod("branch", int.class, boolean.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }


        final ClassVisitor writer;
        final int api;
        private static int id=0;
        String className;

        protected Instrumenter(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
            this.writer = classVisitor;
            this.api = api;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces){
            super.visit(version, access, name, signature, superName, interfaces);
            className = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions){
            MethodVisitor methodWriter = writer.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodInstrumenter(api, methodWriter, className, name);
        }
    }




    public static class MethodInstrumenter extends MethodVisitor {

        record LogFalseBranch(Label enter, int branchId, Label exit){};


        final MethodVisitor writer;
        private List<LogFalseBranch> falseBranches;
        private int line;
        private final String className;
        private final String funName;
        protected MethodInstrumenter(int api, MethodVisitor methodVisitor, String className, String funName) {
            super(api, methodVisitor);
            this.writer = methodVisitor;
            this.className = className;
            this.funName = funName;
            this.falseBranches = new ArrayList<>();
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex){
            switch (opcode){
                case  ISTORE, LSTORE, FSTORE, DSTORE, ASTORE:
                    int nextId = Instrumenter.id++;
                    writer.visitVarInsn(opcode, varIndex);
                    writer.visitIntInsn(BIPUSH, nextId);
                    writer.visitVarInsn(ILOAD, varIndex);
                    writer.visitMethodInsn(INVOKESTATIC,
                            "ch/epfl/systemf/EventLogger",
                            "store",
                            Instrumenter.STORE_TYPE,
                            false);

                    EventDescriptionLogger.store(nextId, varIndex, className, funName, line);
                    return;
                default:
                    break;
            }
            writer.visitVarInsn(opcode, varIndex);
        }

        @Override
        public void visitMethodInsn(int opcode,
                                    String owner,
                                    String name,
                                    String descriptor,
                                    boolean isInterface){
            writer.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label){
            if(opcode==IF_ICMPGE || opcode==IF_ICMPNE){
                int nextId = Instrumenter.id++;
                EventDescriptionLogger.branch(nextId, className, funName, line);

                Label falseBranch = new Label();
                writer.visitJumpInsn(opcode, falseBranch);
                writer.visitIntInsn(BIPUSH, nextId);
                writer.visitInsn(ICONST_1);
                writer.visitMethodInsn(INVOKESTATIC,
                        "ch/epfl/systemf/EventLogger",
                        "branch",
                        Instrumenter.BRANCH_TYPE,
                        false);
                falseBranches.add(new LogFalseBranch(falseBranch, nextId, label));


            }else{
                writer.visitJumpInsn(opcode, label);
            }
        }

        @Override
        public void visitIincInsn(int varIndex, int increment){
            int nextId = Instrumenter.id++;
            EventDescriptionLogger.store(nextId, varIndex, className, funName, line);
            writer.visitIincInsn(varIndex,increment);
            writer.visitIntInsn(BIPUSH, nextId);
            writer.visitVarInsn(ILOAD, varIndex);
            writer.visitMethodInsn(INVOKESTATIC,
                    "ch/epfl/systemf/EventLogger",
                    "store",
                    Instrumenter.STORE_TYPE,
                    false);
        }

        @Override
        public void visitLabel(Label label){
            line = 0;
            writer.visitLabel(label);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals){
            for(LogFalseBranch branch : falseBranches){
                writer.visitLabel(branch.enter);
                writer.visitIntInsn(BIPUSH, branch.branchId);
                writer.visitInsn(ICONST_0);
                writer.visitMethodInsn(INVOKESTATIC,
                        "ch/epfl/systemf/EventLogger",
                        "branch",
                        Instrumenter.BRANCH_TYPE,
                        false);
                writer.visitJumpInsn(GOTO,branch.exit);
            }

            writer.visitMaxs(maxStack, maxLocals);
        }
    }
}
