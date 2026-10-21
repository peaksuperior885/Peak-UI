package com.peak885.ui.shader;

import org.anarres.cpp.CppReader;
import org.anarres.cpp.LexerSource;
import org.anarres.cpp.Preprocessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.stream.Collectors;


public class Preprocessing {

    public static String preprocess(String resourcePath) {
                try {
            String source = readResource(resourcePath);

            String version = "";

            String[] lines = source.split("\n", -1);

            if (lines.length > 0 && lines[0].trim().startsWith("#version")) {
                version = lines[0].trim() + "\n";

                source = String.join(
                        "\n",
                        Arrays.copyOfRange(lines, 1, lines.length)
                );
            }

            Deque<String> includeStack = new ArrayDeque<>();
            includeStack.push(resourcePath);

            source = resolveIncludes(source, resourcePath, includeStack);

            Preprocessor cpp = new Preprocessor();

            cpp.addInput(new LexerSource(
                    new StringReader(source),
                    true
            ));

            String processed;

            try (CppReader reader = new CppReader(cpp);
                 BufferedReader bufferedReader = new BufferedReader(reader)) {

                processed = bufferedReader
                        .lines()
                        .collect(Collectors.joining("\n"));
            }

            return version + processed;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to preprocess shader: " + resourcePath,
                    e
            );
        }
    }

    private static String resolveIncludes(
            String source,
            String parentPath,
            Deque<String> includeStack
    ) {
        StringBuilder result = new StringBuilder();

        String[] lines = source.split("\n", -1);

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("#include")) {
                int firstQuote = trimmed.indexOf('"');
                int lastQuote = trimmed.lastIndexOf('"');

                if (firstQuote == -1 || lastQuote <= firstQuote) {
                    throw new IllegalStateException(
                            "Invalid include directive in "
                                    + parentPath + ": " + line
                    );
                }

                String includePath = trimmed.substring(
                        firstQuote + 1,
                        lastQuote
                );

                String resolvedPath;

                if (includePath.startsWith("/")) {
                    resolvedPath = includePath;
                } else {
                    int slash = parentPath.lastIndexOf('/');

                    String parentDirectory =
                            slash >= 0
                                    ? parentPath.substring(0, slash + 1)
                                    : "/";

                    resolvedPath = parentDirectory + includePath;
                }

                resolvedPath = normalizeResourcePath(resolvedPath);

                if (includeStack.contains(resolvedPath)) {
                    String cycle = String.join(
                            " -> ",
                            includeStack
                    ) + " -> " + resolvedPath;

                    throw new IllegalStateException(
                            "Circular #include detected: " + cycle
                    );
                }

                result.append(
                        resolveShaderResource(resolvedPath, includeStack)
                );

                result.append('\n');

            } else {
                result.append(line);
                result.append('\n');
            }
        }

        return result.toString();
    }

    private static String resolveShaderResource(
            String resourcePath,
            Deque<String> includeStack
    ) {
        try {
            String source = readResource(resourcePath);

            includeStack.push(resourcePath);
            try {
                return resolveIncludes(source, resourcePath, includeStack);
            } finally {
                includeStack.pop();
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to read shader resource: "
                            + resourcePath,
                    e
            );
        }
    }

    private static String normalizeResourcePath(String path) {
        String[] parts = path.split("/");
        java.util.ArrayList<String> stack = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
                continue;
            }
            stack.add(part);
        }
        if (stack.isEmpty()) {
            return "/";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : stack) {
            sb.append('/').append(part);
        }
        return sb.toString();
    }

    private static String readResource(
            String resourcePath
    ) throws IOException {

        try (InputStream in =
                     Preprocessing.class.getResourceAsStream(resourcePath)) {

            if (in == null) {
                throw new IllegalStateException(
                        "Shader resource not found: "
                                + resourcePath
                );
            }

            try (InputStreamReader input =
                         new InputStreamReader(
                                 in,
                                 StandardCharsets.UTF_8
                         );
                 BufferedReader reader =
                         new BufferedReader(input)) {

                return reader.lines()
                        .collect(Collectors.joining("\n"));
            }
        }
    }
}
