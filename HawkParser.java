import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HawkParser {

    enum TokenType {
        PROGRAM, BEGIN, END, IF, THEN, ELSE, INPUT, OUTPUT, INT, FLOAT, DOUBLE,
        WHILE, LOOP, CALL,
        ID, NUM,
        ASSIGN, LT, GT, EQ, NEQ, PLUS, MINUS, MUL, DIV,
        LPAREN, RPAREN, SEMI, COMMA, COLON,
        EOF
    }

    static class Token {
        final TokenType type;
        final String lexeme;
        final int line;

        Token(TokenType type, String lexeme, int line) {
            this.type = type;
            this.lexeme = lexeme;
            this.line = line;
        }

        @Override
        public String toString() {
            return type + "('" + lexeme + "') at line " + line;
        }
    }

    static class ParseException extends RuntimeException {
        ParseException(String message) {
            super(message);
        }
    }

    static class Lexer {
        private final String input;
        private int pos = 0;
        private int line = 1;

        private static final Map<String, TokenType> RESERVED = new HashMap<>();
        static {
            RESERVED.put("program", TokenType.PROGRAM);
            RESERVED.put("begin", TokenType.BEGIN);
            RESERVED.put("end", TokenType.END);
            RESERVED.put("if", TokenType.IF);
            RESERVED.put("then", TokenType.THEN);
            RESERVED.put("else", TokenType.ELSE);
            RESERVED.put("input", TokenType.INPUT);
            RESERVED.put("output", TokenType.OUTPUT);
            RESERVED.put("int", TokenType.INT);
            RESERVED.put("float", TokenType.FLOAT);
            RESERVED.put("double", TokenType.DOUBLE);
            RESERVED.put("while", TokenType.WHILE);
            RESERVED.put("loop", TokenType.LOOP);
            RESERVED.put("call", TokenType.CALL);
        }

        Lexer(String input) {
            this.input = input;
        }

        Token nextToken() {
            skipWhitespace();

            if (pos >= input.length()) {
                return new Token(TokenType.EOF, "EOF", line);
            }

            char c = input.charAt(pos);
            int tokenLine = line;

            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                pos++;
                while (pos < input.length()) {
                    char ch = input.charAt(pos);
                    if (Character.isLetterOrDigit(ch) || ch == '_') pos++;
                    else break;
                }
                String lexeme = input.substring(start, pos);
                TokenType reserved = RESERVED.get(lexeme);
                if (reserved != null) return new Token(reserved, lexeme, tokenLine);
                return new Token(TokenType.ID, lexeme, tokenLine);
            }

            if (Character.isDigit(c)) {
                return scanNumber();
            }

            switch (c) {
                case ':':
                    if (peekNext('=')) {
                        pos += 2;
                        return new Token(TokenType.ASSIGN, ":=", tokenLine);
                    }
                    pos++;
                    return new Token(TokenType.COLON, ":", tokenLine);
                case '<':
                    if (peekNext('>')) {
                        pos += 2;
                        return new Token(TokenType.NEQ, "<>", tokenLine);
                    }
                    pos++;
                    return new Token(TokenType.LT, "<", tokenLine);
                case '>':
                    pos++;
                    return new Token(TokenType.GT, ">", tokenLine);
                case '=':
                    pos++;
                    return new Token(TokenType.EQ, "=", tokenLine);
                case '+':
                    pos++;
                    return new Token(TokenType.PLUS, "+", tokenLine);
                case '-':
                    pos++;
                    return new Token(TokenType.MINUS, "-", tokenLine);
                case '*':
                    pos++;
                    return new Token(TokenType.MUL, "*", tokenLine);
                case '/':
                    pos++;
                    return new Token(TokenType.DIV, "/", tokenLine);
                case '(':
                    pos++;
                    return new Token(TokenType.LPAREN, "(", tokenLine);
                case ')':
                    pos++;
                    return new Token(TokenType.RPAREN, ")", tokenLine);
                case ';':
                    pos++;
                    return new Token(TokenType.SEMI, ";", tokenLine);
                case ',':
                    pos++;
                    return new Token(TokenType.COMMA, ",", tokenLine);
                default:
                    error("Illegal symbol '" + c + "'", tokenLine);
                    return null;
            }
        }

        private Token scanNumber() {
            int tokenLine = line;
            int start = pos;
            int beforeDot = 0;
            int afterDot = 0;

            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                beforeDot++;
                pos++;
            }

            if (beforeDot > 10) {
                error("Illegal number '" + input.substring(start, pos) + "'", tokenLine);
            }

            if (pos < input.length() && input.charAt(pos) == '.') {
                pos++;
                int fracStart = pos;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    afterDot++;
                    pos++;
                }
                if (afterDot == 0) {
                    error("Illegal number '" + input.substring(start, pos) + "'", tokenLine);
                }
                if (afterDot > 10) {
                    error("Illegal number '" + input.substring(start, pos) + "'", tokenLine);
                }
                if (fracStart == pos) {
                    error("Illegal number '" + input.substring(start, pos) + "'", tokenLine);
                }
            }

            if (pos < input.length()) {
                char next = input.charAt(pos);
                if (Character.isLetter(next) || next == '_') {
                    while (pos < input.length()) {
                        char ch = input.charAt(pos);
                        if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.') pos++;
                        else break;
                    }
                    error("Illegal identifier '" + input.substring(start, pos) + "'", tokenLine);
                }
            }

            return new Token(TokenType.NUM, input.substring(start, pos), tokenLine);
        }

        private boolean peekNext(char expected) {
            return pos + 1 < input.length() && input.charAt(pos + 1) == expected;
        }

        private void skipWhitespace() {
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == '\n') {
                    line++;
                    pos++;
                } else if (Character.isWhitespace(c)) {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private void error(String msg, int errorLine) {
            throw new ParseException("ERROR !! " + msg + " in Line " + errorLine + ".");
        }
    }

    static class Parser {
        private final Lexer lexer;
        private final List<String> output = new ArrayList<>();
        private final Set<String> symbols = new HashSet<>();
        private Token current;

        Parser(Lexer lexer) {
            this.lexer = lexer;
            this.current = lexer.nextToken();
        }

        List<String> parse() {
            program();
            expect(TokenType.EOF, "Unexpected tokens after program end");
            return output;
        }

        List<String> getOutput() {
            return output;
        }

        private void program() {
            emit("PROGRAM");
            expect(TokenType.PROGRAM, "Expected 'program'");

            if (current.type == TokenType.ID) {
                declSec();
            }

            expect(TokenType.BEGIN, "Expected 'begin'");
            stmtSec();
            expect(TokenType.END, "Expected 'end'");
            expect(TokenType.SEMI, "Semicolon missing");
        }

        private void declSec() {
            emit("DECL_SEC");
            decl();
            while (current.type == TokenType.ID) {
                emit("DECL_SEC");
                decl();
            }
        }

        private void decl() {
            emit("DECL");
            List<String> ids = idListForDeclaration();
            expect(TokenType.COLON, "Expected ':'");
            type();
            expect(TokenType.SEMI, "Semicolon missing");
            for (String id : ids) {
                if (symbols.contains(id)) {
                    error("identifier redeclared", current.line);
                }
                symbols.add(id);
            }
        }

        private List<String> idListForDeclaration() {
            List<String> ids = new ArrayList<>();
            emit("ID_LIST");
            ids.add(readDeclaredId());
            while (current.type == TokenType.COMMA) {
                expect(TokenType.COMMA, "Expected ','");
                emit("ID_LIST");
                ids.add(readDeclaredId());
            }
            return ids;
        }

        private void stmtSec() {
            emit("STMT_SEC");
            stmt();
            while (startsStatement(current.type)) {
                emit("STMT_SEC");
                stmt();
            }
        }

        private boolean startsStatement(TokenType t) {
            return t == TokenType.ID || t == TokenType.IF || t == TokenType.WHILE
                    || t == TokenType.INPUT || t == TokenType.OUTPUT || t == TokenType.CALL;
        }

        private void stmt() {
            emit("STMT");
            switch (current.type) {
                case ID -> assign();
                case IF -> ifStmt();
                case WHILE -> whileStmt();
                case INPUT -> inputStmt();
                case OUTPUT -> outputStmt();
                case CALL -> funcStmt();
                default -> error("Unexpected statement", current.line);
            }
        }

        private void assign() {
            emit("ASSIGN");
            requireDeclared(current);
            expect(TokenType.ID, "Expected identifier");
            expect(TokenType.ASSIGN, "Expected ':='");
            expr();
            expect(TokenType.SEMI, "Semicolon missing");
        }

        private void ifStmt() {
            emit("IF_STMT");
            expect(TokenType.IF, "Expected 'if'");
            comp();
            expect(TokenType.THEN, "Expected 'then'");
            stmtSec();
            if (current.type == TokenType.ELSE) {
                expect(TokenType.ELSE, "Expected 'else'");
                stmtSec();
            }
            expect(TokenType.END, "Expected 'end'");
            expect(TokenType.IF, "Expected 'if'");
            expect(TokenType.SEMI, "Semicolon missing");
        }

        private void whileStmt() {
            emit("WHILE_STMT");
            expect(TokenType.WHILE, "Expected 'while'");
            comp();
            expect(TokenType.LOOP, "Expected 'loop'");
            stmtSec();
            expect(TokenType.END, "Expected 'end'");
            expect(TokenType.LOOP, "Expected 'loop'");
            expect(TokenType.SEMI, "Semicolon missing");
        }

        private void inputStmt() {
            emit("INPUT");
            expect(TokenType.INPUT, "Expected 'input'");
            idListUsage();
            expect(TokenType.SEMI, "Semicolon missing");
        }

        private void outputStmt() {
            emit("OUTPUT");
            expect(TokenType.OUTPUT, "Expected 'output'");
            if (current.type == TokenType.NUM) {
                expect(TokenType.NUM, "Expected number");
            } else {
                idListUsage();
            }
            expect(TokenType.SEMI, "Semicolon missing");
        }

        private void funcStmt() {
            func();
            expect(TokenType.SEMI, "Semicolon missing");
        }

        private void expr() {
            emit("EXPR");
            factor();
            if (current.type == TokenType.PLUS || current.type == TokenType.MINUS) {
                advance();
                expr();
            }
        }

        private void factor() {
            emit("FACTOR");
            operand();
            if (current.type == TokenType.MUL || current.type == TokenType.DIV) {
                advance();
                factor();
            }
        }

        private void operand() {
            emit("OPERAND");
            switch (current.type) {
                case NUM -> expect(TokenType.NUM, "Expected number");
                case ID -> {
                    requireDeclared(current);
                    expect(TokenType.ID, "Expected identifier");
                }
                case LPAREN -> {
                    expect(TokenType.LPAREN, "Expected '('");
                    expr();
                    expect(TokenType.RPAREN, "Expected ')'");
                }
                case CALL -> func();
                default -> error("Expected operand", current.line);
            }
        }

        private void comp() {
            emit("COMP");
            expect(TokenType.LPAREN, "Expected '('");
            operand();
            if (current.type == TokenType.EQ || current.type == TokenType.NEQ
                    || current.type == TokenType.GT || current.type == TokenType.LT) {
                advance();
            } else {
                error("Expected comparison operator", current.line);
            }
            operand();
            expect(TokenType.RPAREN, "Expected ')'");
        }

        private void type() {
            if (current.type == TokenType.INT || current.type == TokenType.FLOAT || current.type == TokenType.DOUBLE) {
                advance();
            } else {
                error("Expected type", current.line);
            }
        }

        private void func() {
            emit("FUNC");
            expect(TokenType.CALL, "Expected 'call'");
            expect(TokenType.ID, "Expected function identifier");
            expect(TokenType.LPAREN, "Expected '('");
            if (current.type == TokenType.NUM) {
                expect(TokenType.NUM, "Expected number");
            } else {
                idListUsage();
            }
            expect(TokenType.RPAREN, "Expected ')'");
        }

        private void idListUsage() {
            emit("ID_LIST");
            requireDeclared(current);
            expect(TokenType.ID, "Expected identifier");
            while (current.type == TokenType.COMMA) {
                expect(TokenType.COMMA, "Expected ','");
                emit("ID_LIST");
                requireDeclared(current);
                expect(TokenType.ID, "Expected identifier");
            }
        }

        private String readDeclaredId() {
            Token tok = current;
            expect(TokenType.ID, "Expected identifier");
            return tok.lexeme;
        }

        private void requireDeclared(Token tok) {
            if (tok.type != TokenType.ID) {
                error("Expected identifier", tok.line);
            }
            if (!symbols.contains(tok.lexeme)) {
                error("identifier not declared", tok.line);
            }
        }

        private void expect(TokenType expected, String message) {
            if (current.type != expected) {
                error(message, current.line);
            }
            advance();
        }

        private void advance() {
            current = lexer.nextToken();
        }

        private void emit(String rule) {
            output.add(rule);
        }

        private void error(String msg, int line) {
            throw new ParseException("ERROR !! " + msg + " in Line " + line + ".");
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java HawkParser <input-file>");
            return;
        }        try {
            String source = Files.readString(Path.of(args[0]));
            Lexer lexer = new Lexer(source);
            Parser parser = new Parser(lexer);
            try {
                List<String> result = parser.parse();
                for (String line : result) {
                    System.out.println(line);
                }
            } catch (ParseException e) {
                for (String line : parser.getOutput()) {
                    System.out.println(line);
                }
                System.out.println(e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("Could not read input file: " + e.getMessage());
        }
    }
}
