import org.objectweb.asm.tree.*
import org.objectweb.asm.Opcodes.*

object Instructions {
    def monitorEnterOnThis: InsnList = 
        insnList(
            new VarInsnNode(ALOAD, 0),
            new InsnNode(MONITORENTER)
        )
    
    def monitorExitOnThis: InsnList = 
        insnList(
            new VarInsnNode(ALOAD, 0),
            new InsnNode(MONITOREXIT)
        )

    def testCall = new MethodInsnNode(INVOKESTATIC, "Test", "test", "()V", false)

    def insnList(insns: AbstractInsnNode*) = 
        val stack = new InsnList
        for(insn <- insns) {
            stack.add(insn)
        }
        stack
    
     

    def printlnThis = 
        insnList(
            new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"),
            new VarInsnNode(ALOAD, 0),
            new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false)
        )
    
    
    def printlnStrWithThread(str: String) = 
        insnList(
            new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"),
            new InsnNode(DUP),
            new MethodInsnNode(INVOKESTATIC, "Tracer", "currentThreadLabel", "()Ljava/lang/String;", false),
            new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/Object;)V", false),
            new LdcInsnNode(str),
            new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false)
        )

    def appendLog = new MethodInsnNode(INVOKESTATIC, "Tracer", "appendLog", "(Ljava/lang/Object;)V", false)
    def merge2 = new MethodInsnNode(INVOKESTATIC, "Tracer", "merge", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;", false)
    def mergeSafe2 = new MethodInsnNode(INVOKESTATIC, "Tracer", "mergeSafe", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;", false)
    def merge3 = new MethodInsnNode(INVOKESTATIC, "Tracer", "merge", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;", false)
}