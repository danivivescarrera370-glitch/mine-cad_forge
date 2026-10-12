package name.minecad.client.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

public final class BindingInput {
    private BindingInput() {
    }

    public static boolean isBindingActive(String binding, long windowHandle) {
        List<String> tokens = tokenize(binding);
        if (tokens.isEmpty()) {
            return false;
        }
        for (String token : tokens) {
            // Scroll is an event, not a held state.
            if ("MOUSE_SCROLL".equals(token)) {
                return false;
            }
            if (!isTokenDown(token, windowHandle)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isScrollBindingActive(String binding, long windowHandle) {
        List<String> tokens = tokenize(binding);
        if (tokens.isEmpty()) {
            return true;
        }
        boolean sawScrollToken = false;
        for (String token : tokens) {
            if ("MOUSE_SCROLL".equals(token)) {
                sawScrollToken = true;
                continue;
            }
            if (!isTokenDown(token, windowHandle)) {
                return false;
            }
        }
        return sawScrollToken;
    }

    public static int mouseButtonForBinding(String binding, int fallbackButton) {
        for (String token : tokenize(binding)) {
            Integer button = mouseButton(token);
            if (button != null) {
                return button;
            }
        }
        return fallbackButton;
    }

    private static boolean isTokenDown(String token, long windowHandle) {
        Integer button = mouseButton(token);
        if (button != null) {
            return GLFW.glfwGetMouseButton(windowHandle, button) == GLFW.GLFW_PRESS;
        }

        Integer key = keyboardKey(token);
        return key != null && GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_PRESS;
    }

    private static Integer mouseButton(String token) {
        return switch (token) {
            case "MOUSE_LEFT_CLICK", "MOUSE_LEFT" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case "MOUSE_RIGHT_CLICK", "MOUSE_RIGHT" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case "MOUSE_SCROLL_CLICK", "MOUSE_MIDDLE_CLICK", "MOUSE_MIDDLE" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            default -> null;
        };
    }

    private static Integer keyboardKey(String token) {
        if (token.length() == 1) {
            char c = token.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return GLFW.GLFW_KEY_A + (c - 'A');
            }
            if (c >= '0' && c <= '9') {
                return GLFW.GLFW_KEY_0 + (c - '0');
            }
        }

        if (token.length() == 2 && token.charAt(0) == 'F' && Character.isDigit(token.charAt(1))) {
            int n = token.charAt(1) - '0';
            if (n >= 1 && n <= 9) {
                return GLFW.GLFW_KEY_F1 + (n - 1);
            }
        }
        if (token.length() == 3 && token.startsWith("F")
                && Character.isDigit(token.charAt(1)) && Character.isDigit(token.charAt(2))) {
            int n = Integer.parseInt(token.substring(1));
            if (n >= 10 && n <= 12) {
                return GLFW.GLFW_KEY_F1 + (n - 1);
            }
        }

        return switch (token) {
            case "SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "CTRL", "CONTROL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
            case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
            case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "UP", "ARROW_UP" -> GLFW.GLFW_KEY_UP;
            case "DOWN", "ARROW_DOWN" -> GLFW.GLFW_KEY_DOWN;
            case "LEFT", "ARROW_LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT", "ARROW_RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            default -> null;
        };
    }

    private static List<String> tokenize(String binding) {
        if (binding == null) {
            return List.of();
        }
        String normalized = binding.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        String[] rawTokens = normalized.toUpperCase(Locale.ROOT).split("\\+");
        List<String> tokens = new ArrayList<>(rawTokens.length);
        for (String raw : rawTokens) {
            String token = raw.trim().replace('-', '_').replace(' ', '_');
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}

