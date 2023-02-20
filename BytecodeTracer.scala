//> using scala "3.3.0-RC2"
//> using lib "org.ow2.asm:asm-all:6.0_BETA"
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.FileInputStream
import java.io.File
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import collection.JavaConverters.asScalaBufferConverter
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.ClassWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.objectweb.asm.Handle
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.*
import org.objectweb.asm.Opcodes.*
import Instructions.*
import java.nio.file.StandardCopyOption

object BytecodeTracer {

    case class InsnData(insn: AbstractInsnNode, method: MethodNode)

    val textifier = new Textifier()
    def lastInstructionPretty = 
        textifier.text.asScala.last.toString().filterNot(_ == '\n').trim()
    val printifier = new TraceMethodVisitor(textifier)

    val config: List[(String, Transformer)] = List(
       ".*" -> InsertBefore(appendInsnLog),
    )

    trait Transformer {
        def transform(insn: InsnData, stack: InsnList): Unit
    }
    case class InsertAfter(fn: InsnData => InsnList) extends Transformer {
        override def transform(insn: InsnData, stack: InsnList): Unit = 
            val toInsert = fn(insn)
            stack.insert(insn.insn, toInsert)
    }

    case class InsertBefore(fn: InsnData => InsnList) extends Transformer {
        override def transform(insn: InsnData, stack: InsnList): Unit = 
            val toInsert = fn(insn)
            stack.insertBefore(insn.insn, toInsert)
    }

    class ComposedTransformer(transformers: Transformer*) extends Transformer {
        override def transform(insn: InsnData, stack: InsnList): Unit = 
            for(transformer <- transformers) {
                transformer.transform(insn, stack)
            }
    }

    case class InsertAround(fnBefore: InsnData => InsnList, fnAfter: InsnData => InsnList) 
        extends ComposedTransformer(InsertBefore(fnBefore), InsertAfter(fnAfter))


    def debug(transformer: Transformer): Transformer = new Transformer {
        override def transform(insn: InsnData, stack: InsnList): Unit = 
            println(s"Transforming \"$lastInstructionPretty\" with ${transformer.getClass().getSimpleName()}")
            transformer.transform(insn, stack)
    }

    @main
    def tracer(fileName: String, others: String*) =
        val outFile = others.headOption
        val file = new FileInputStream(new File(fileName))
        val classReader = new ClassReader(file)
        val cn = new ClassNode()
        classReader.accept(cn, 0)

        injectInClass(cn)

        val classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
        cn.accept(classWriter)
        val destPath = Paths.get(outFile.getOrElse(s"output/$file"))
        Files.write(destPath, classWriter.toByteArray())
        Files.copy(Paths.get("tracer/Tracer.class"), destPath.getParent().resolve("Tracer.class"), StandardCopyOption.REPLACE_EXISTING)
        Files.copy(Paths.get("tracer/Tracer$1.class"), destPath.getParent().resolve("Tracer$1.class"), StandardCopyOption.REPLACE_EXISTING)
        Files.copy(Paths.get("tracer/Tracer$TeeStream.class"), destPath.getParent().resolve("Tracer$TeeStream.class"), StandardCopyOption.REPLACE_EXISTING)

    def injectInClass(cn: ClassNode) = {
        for(method <- cn.methods.asScala) {
            injectMethod(method.asInstanceOf[MethodNode])
        }
    }   

    def injectMethod(mn: MethodNode) = {
        val insns = mn.instructions.toArray()
        val newStack = new InsnList()
        newStack.add(appendMethodEntry(mn));
        newStack.add(mn.instructions)
        for (instruction <- insns) {
            val insnData = InsnData(instruction, mn)
            transform(insnData, newStack)
        }
        mn.instructions = newStack
    }


    def transform(insn: InsnData, stack: InsnList) = 
        insn.insn.accept(printifier)
        val insnText = lastInstructionPretty
        config.foreach {
            case (regex, transformer) if insnText.matches(regex) =>
                transformer.transform(insn, stack)
            case _ =>
        }

    def appendMethodEntry(mn: MethodNode) = {
        insnList(
            new LdcInsnNode(mn.name + mn.desc),
            new MethodInsnNode(INVOKESTATIC, "Tracer", "enterMethod", "(Ljava/lang/String;)V", false)
        )
    }
    
    def appendInsnLog(insnData: InsnData) = {
        val insn = insnData.insn
        insn match {
            case varInsn: VarInsnNode if insn.getOpcode() == ALOAD && !insnData.method.name.equals("<init>") && !insnData.method.name.contains("toString") =>
                val copiedLoad = new VarInsnNode(ALOAD, varInsn.`var`)
                insnList(
                    new LdcInsnNode(lastInstructionPretty + " <-"),
                    copiedLoad,
                    mergeSafe2,
                    appendLog
                )
            case _ if insn.getOpcode() == ARETURN =>
                insnList(
                    new InsnNode(DUP),
                    new LdcInsnNode(lastInstructionPretty + " <-"),
                    new InsnNode(SWAP),
                    merge2,
                    appendLog
                )
            case _ => 
                insnList(
                    new LdcInsnNode(lastInstructionPretty),
                    appendLog
                )
        }

    }

}
