import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

public class Tracer {

    static class TeeStream extends PrintStream {
        private PrintStream dupOut;
        public TeeStream(PrintStream out, PrintStream dupOut) {
            super(out);
            this.dupOut = dupOut;
        }

        public void write(byte buf[], int off, int len) {
            try {
                super.write(buf, off, len);
                dupOut.write(buf, off, len);
            } catch (Exception e) {
            }
        }
        public void flush() {
            super.flush();
            dupOut.flush();
        }

    }

    private static volatile String[] buffer = new String[1024*1024];
    private static volatile int top = 0;
    private static final int THREADS_MODULO = 32277;
    private static volatile boolean[] wasInToString = new boolean[THREADS_MODULO];

    public static final String ENTER_METHOD = "ENTER_METHOD";

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                dumpLog();
            }
        });
        try {
            File logFile = new File("execlog.log");
            PrintStream logOut = new PrintStream(new FileOutputStream(logFile, true));
            PrintStream teeStdOut = new TeeStream(System.out, logOut);
            PrintStream teeStdErr = new TeeStream(System.err, logOut);
            System.setOut(teeStdOut);
            System.setErr(teeStdErr);
        } catch(Exception ex) {
            ex.printStackTrace();
        }

    }

    public static String merge(Object o1, Object o2) {
        return Objects.toString(o1) + " " + Objects.toString(o2); 
    }

    public static String mergeSafe(Object o1, Object o2) {
        if(wasInToString == null) wasInToString = new boolean[THREADS_MODULO];
        if(wasInToString[Thread.currentThread().hashCode() % THREADS_MODULO]) {
            return Objects.toString(o1) + " [ADDITIONAL INFO UNAVAILABLE]";
        } else {
            wasInToString[Thread.currentThread().hashCode() % THREADS_MODULO] = true;
            String result = Objects.toString(o1) + " " + Objects.toString(o2);
            wasInToString[Thread.currentThread().hashCode() % THREADS_MODULO] = false;
            return result;
        }
        
    }

    public static String merge(Object o1, Object o2, Object o3) {
        return o1.toString() + " " + o2.toString() + " " + o3.toString();
    }

    public static void appendLog(Object insn) {
        synchronized(buffer) {
            if(!wasInToString[Thread.currentThread().hashCode() % THREADS_MODULO]) buffer[top++] = currentThreadLabel() + "|" + insn;
        }
    }


    public static void enterMethod(String method) {
        appendLog(ENTER_METHOD + "->" + method);
    }

    private static void dumpLog() {
        System.out.println(" -- Debugging tracer logs");
        Scanner sc = new Scanner(System.in);
        main:
        while(true) {
            System.out.println("Select:\n\t1. Print thread execution \n\t2. Exit");
            int opt = sc.nextInt();
            sc.nextLine();
            switch(opt) {
                case 1:
                    System.out.println("Enter pattern:");
                    String pattern = sc.nextLine();
                    String methodPattern = "";
                    String threadPattern = "";
                    if(pattern.contains(";")) {
                        threadPattern = pattern.split(";")[0];
                        methodPattern = (pattern.split(";").length < 2) ? "" : pattern.split(";")[1];
                    }
                    Map<String, LinkedList<String>> methodThreads = new HashMap<>();
                    for(String s : buffer) {
                        if(s == null) {
                            System.out.println(" -- End of logs");
                            break main;
                        } 
                        String[] logInfo = s.split("\\|");
                        String thread = logInfo[0];
                        String insn = logInfo[1];
                        methodThreads.putIfAbsent(thread, new LinkedList<>());
                        LinkedList<String> threadStack = methodThreads.get(thread);
                        String currentMethod = (threadStack.isEmpty()) ? "UNKNOWN" : threadStack.peek();
                        if(insn.contains(ENTER_METHOD)) {
                            String method = insn.split("->")[1];
                            if(thread.contains(threadPattern) && method.contains(methodPattern)) {
                                System.out.println("\n[" + thread + "] Entering method " + method + " from " + currentMethod);
                            }
                            threadStack.push(method);
                        } else {
                            if(insn.contains("RETURN")) {
                                String returnFrom = threadStack.pop();
                                String returnedTo = (threadStack.isEmpty()) ? "UNKNOWN" : threadStack.peek();
                                if(thread.contains(threadPattern) && (returnFrom.contains(methodPattern) || returnedTo.contains(methodPattern))) {
                                    System.out.println("\n[" + thread + "] Returning to " + returnedTo + " from " + returnFrom + " with " + insn);
                                }
                            } else {
                                if(thread.contains(threadPattern) && currentMethod.contains(methodPattern)) {
                                    System.out.println("[" + thread + "] " + insn);
                                }
                            }
                        }
                    
                    }
                    break;
                case 2:
                    return;
            }
        }
    }

    public static String currentThreadLabel() {
        return Thread.currentThread().getName();
    }
}
