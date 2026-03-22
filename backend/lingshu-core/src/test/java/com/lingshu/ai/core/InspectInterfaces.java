import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import java.lang.reflect.Method;

public class InspectInterfaces {
    public static void main(String[] args) {
        System.out.println("--- ChatModel Methods ---");
        for (Method m : ChatModel.class.getMethods()) {
            if (m.getName().contains("estimate")) {
                System.out.println(provideSignature(m));
            }
        }
        System.out.println("--- StreamingChatModel Methods ---");
        for (Method m : StreamingChatModel.class.getMethods()) {
            if (m.getName().contains("estimate")) {
                System.out.println(provideSignature(m));
            }
        }
    }

    private static String provideSignature(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getReturnType().getSimpleName()).append(" ");
        sb.append(m.getName()).append("(");
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            sb.append(params[i].getSimpleName());
            if (i < params.length - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }
}
