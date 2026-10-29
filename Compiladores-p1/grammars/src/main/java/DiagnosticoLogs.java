import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.antlr.v4.runtime.*;

/** Comprobador de cobertura y sintaxis usando realmente los lexer/parser generados por ANTLR. */
public class DiagnosticoLogs {
    private static class Errores extends BaseErrorListener {
        final List<String> errores = new ArrayList<>();
        @Override public void syntaxError(Recognizer<?, ?> recognizer, Object simbolo,
                int linea, int columna, String mensaje, RecognitionException e) {
            errores.add("Linea " + linea + ", columna " + (columna + 1) + ": " + mensaje);
        }
    }

    private static boolean revisar(Path archivo) throws IOException {
        String texto = Files.readString(archivo, StandardCharsets.UTF_8);
        long lineasNoVacias = texto.lines().filter(s -> !s.isBlank()).count();
        if (!texto.endsWith("\n") && !texto.endsWith("\r")) texto += "\n";
        PokemonLogLexer lexer = new PokemonLogLexer(CharStreams.fromString(texto, archivo.toString()));
        Errores erroresLexer = new Errores();
        lexer.removeErrorListeners();
        lexer.addErrorListener(erroresLexer);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PokemonLogParser parser = new PokemonLogParser(tokens);
        Errores erroresParser = new Errores();
        parser.removeErrorListeners();
        parser.addErrorListener(erroresParser);
        parser.gameLog();
        tokens.fill();
        Map<String, Integer> distribucion = new TreeMap<>();
        int invalidas = 0, reconocidas = 0;
        for (Token tok : tokens.getTokens()) {
            if (tok.getType() == Token.EOF) continue;
            if (tok.getType() == PokemonLogLexer.INVALID_LINE) {
                invalidas++;
                System.out.println("  LINEA NO RECONOCIDA " + tok.getLine() + ": " + tok.getText().strip());
            } else {
                reconocidas++;
                String nombre = PokemonLogLexer.VOCABULARY.getSymbolicName(tok.getType());
                distribucion.merge(nombre, 1, Integer::sum);
            }
        }
        boolean bien = erroresLexer.errores.isEmpty() && erroresParser.errores.isEmpty()
                && invalidas == 0 && reconocidas == lineasNoVacias;
        System.out.println("\n" + archivo + ": " + (bien ? "ACEPTADO" : "RECHAZADO"));
        System.out.println("  Lineas no vacias: " + lineasNoVacias + ", tokens reconocidos: "
                + reconocidas + ", lineas invalidas: " + invalidas);
        System.out.println("  Tipos de acciones: " + distribucion);
        erroresLexer.errores.forEach(System.out::println);
        erroresParser.errores.forEach(System.out::println);
        return bien;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Uso: java DiagnosticoLogs <log1.txt> [log2.txt ...]");
            System.exit(2);
        }
        boolean todos = true;
        for (String nombre : args) if (!revisar(Paths.get(nombre))) todos = false;
        if (!todos) System.exit(1);
    }
}
