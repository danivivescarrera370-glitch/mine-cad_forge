package name.minecad.player.commands;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandParser {

    public static class ParsedCommand {
        private final List<String> commands;
        private final String outputFile;
        private final boolean appendOutput;
        private final String inputFile;

        public ParsedCommand(List<String> commands, String outputFile, boolean appendOutput, String inputFile) {
            this.commands = commands;
            this.outputFile = outputFile;
            this.appendOutput = appendOutput;
            this.inputFile = inputFile;
        }

        public List<String> getCommands() { return commands; }
        public String getOutputFile() { return outputFile; }
        public boolean isAppendOutput() { return appendOutput; }
        public String getInputFile() { return inputFile; }
        public boolean hasPipe() { return commands.size() > 1; }
        public boolean hasOutputRedirect() { return outputFile != null; }
        public boolean hasInputRedirect() { return inputFile != null; }
    }

    /**
     * Parses a command string and returns the command name and arguments.
     */
    public static String[] parseCommand(String command) {
        if (command == null || command.isEmpty()) {
            return new String[0];
        }
        return command.trim().split("\\s+");
    }

    /**
     * Parses a complex command string with pipes and IO redirection.
     * Supports: command1 | command2 | command3 > file.txt
     *          command < input.txt | command2 >> output.txt
     */
    public static ParsedCommand parseComplexCommand(String commandLine) {
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return new ParsedCommand(new ArrayList<>(), null, false, null);
        }

        commandLine = commandLine.trim();

        // Extract input redirection first (< file)
        String inputFile = null;
        Pattern inputPattern = Pattern.compile("\\s*<\\s*([^\\s|>]+)");
        Matcher inputMatcher = inputPattern.matcher(commandLine);
        if (inputMatcher.find()) {
            inputFile = inputMatcher.group(1);
            commandLine = commandLine.replaceFirst("\\s*<\\s*[^\\s|>]+", "");
        }

        // Extract output redirection (> file or >> file)
        String outputFile = null;
        boolean appendOutput = false;
        Pattern outputPattern = Pattern.compile("\\s*(>>?)\\s*([^\\s|]+)\\s*$");
        Matcher outputMatcher = outputPattern.matcher(commandLine);
        if (outputMatcher.find()) {
            appendOutput = outputMatcher.group(1).equals(">>");
            outputFile = outputMatcher.group(2);
            commandLine = commandLine.replaceFirst("\\s*(>>?)\\s*[^\\s|]+\\s*$", "");
        }

        // Split by pipes and clean up commands
        String[] commandParts = commandLine.split("\\|");
        List<String> commands = new ArrayList<>();

        for (String part : commandParts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                commands.add(trimmed);
            }
        }

        return new ParsedCommand(commands, outputFile, appendOutput, inputFile);
    }

    /**
     * Validates if a command supports piping based on commands.json structure.
     */
    public static boolean isCommandPipeable(String command) {
        // Extract base command and subcommand
        String[] parts = parseCommand(command);
        if (parts.length == 0) return false;

        String baseCommand = parts[0].startsWith("/") ? parts[0].substring(1) : parts[0];

        // Special case for pipe command itself
        if ("pipe".equals(baseCommand)) {
            return true;
        }

        // Check pipeable commands from your JSON structure
        switch (baseCommand) {
            case "selection":
                if (parts.length > 1) {
                    String subcommand = parts[1];
                    return Arrays.asList("show", "delete", "save", "import", "list",
                            "select", "add", "remove", "subtract", "geometry").contains(subcommand);
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * Validates the entire command chain for proper pipe usage.
     */
    public static boolean validatePipeChain(ParsedCommand parsed) {
        if (!parsed.hasPipe()) {
            return true;
        }

        List<String> commands = parsed.getCommands();

        // First command can be any pipeable command
        if (commands.size() > 0 && !isCommandPipeable(commands.get(0))) {
            return false;
        }

        // All subsequent commands in pipe chain must be pipeable
        for (int i = 1; i < commands.size(); i++) {
            if (!isCommandPipeable(commands.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extracts the base command name without leading slash.
     */
    public static String getBaseCommand(String command) {
        String[] parts = parseCommand(command);
        if (parts.length == 0) return "";

        String baseCommand = parts[0];
        return baseCommand.startsWith("/") ? baseCommand.substring(1) : baseCommand;
    }

    /**
     * Gets the subcommand if present.
     */
    public static String getSubCommand(String command) {
        String[] parts = parseCommand(command);
        return parts.length > 1 ? parts[1] : null;
    }
}