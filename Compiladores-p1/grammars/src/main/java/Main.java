import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

public class Main {
    public static void main(String[] args) throws Exception {
        for (String file : args) {
            CharStream in = CharStreams.fromFileName(file);          // UTF-8
            PokemonLogLexer lexer = new PokemonLogLexer(in);
            PokemonLogParser parser = new PokemonLogParser(new CommonTokenStream(lexer));
            ParseTree tree = parser.gameLog();

            LogExtractor ex = new LogExtractor();
            ParseTreeWalker.DEFAULT.walk(ex, tree);

            System.out.println("== " + file);
            System.out.println("Total de turnos: " + ex.turns.size());
            ex.turns.forEach(System.out::println);
            System.out.println("Ganador: " + ex.winner + "  (" + ex.winLine + ")");
        }
    }
}