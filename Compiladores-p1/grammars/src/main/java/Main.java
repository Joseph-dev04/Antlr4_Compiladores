import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

/** Analiza logs, rechaza entradas inválidas y exporta AST y resumen a JSON. */
public class Main {
    static class Errores extends BaseErrorListener {
        final List<String> mensajes = new ArrayList<>();
        @Override public void syntaxError(Recognizer<?,?> r,Object offending,int line,int column,
            String msg,RecognitionException e) {mensajes.add("linea "+line+", columna "+(column+1)+": "+msg);}
    }
    public static boolean analizar(Path archivo) throws IOException {
        String texto=Files.readString(archivo,StandardCharsets.UTF_8);
        // El lexer de líneas recibe siempre una nueva línea final.
        if (!texto.endsWith("\n") && !texto.endsWith("\r")) texto += "\n";
        CharStream input=CharStreams.fromString(texto,archivo.toString());
        PokemonLogLexer lexer=new PokemonLogLexer(input);
        Errores lexErrors=new Errores();lexer.removeErrorListeners();lexer.addErrorListener(lexErrors);
        CommonTokenStream tokens=new CommonTokenStream(lexer);
        PokemonLogParser parser=new PokemonLogParser(tokens);
        Errores parseErrors=new Errores();parser.removeErrorListeners();parser.addErrorListener(parseErrors);
        PokemonLogParser.GameLogContext tree=parser.gameLog();
        tokens.fill();
        for(Token tok:tokens.getTokens()) if(tok.getType()==PokemonLogLexer.INVALID_LINE)
            lexErrors.mensajes.add("linea "+tok.getLine()+": accion no reconocida: "+tok.getText().strip());
        System.out.println("== "+archivo);
        if(!lexErrors.mensajes.isEmpty() || !parseErrors.mensajes.isEmpty()) {
            System.out.println("RECHAZADO");lexErrors.mensajes.forEach(System.out::println);
            parseErrors.mensajes.forEach(System.out::println);return false;
        }
        LogExtractor extractor=new LogExtractor();
        ParseTreeWalker.DEFAULT.walk(extractor,tree);
        Path out=Paths.get("resultados");Files.createDirectories(out);
        String base=archivo.getFileName().toString().replaceFirst("\\.[^.]+$","");
        Path ast=out.resolve(base+"_AST.json"),summary=out.resolve(base+"_resumen.json");
        Files.writeString(ast,ExportadorAST.json(extractor.ast()),StandardCharsets.UTF_8);
        Files.writeString(summary,ExportadorAST.json(extractor.summary()),StandardCharsets.UTF_8);
        Path resumenTexto=out.resolve(base+"_resumen.txt");
        try(java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
            java.io.PrintStream ps=new java.io.PrintStream(buffer,true,StandardCharsets.UTF_8)) {
            extractor.printSummary(ps);
            String contenido=buffer.toString(StandardCharsets.UTF_8);
            Files.writeString(resumenTexto,contenido,StandardCharsets.UTF_8);
            System.out.print(contenido);
        }
        System.out.println("ACEPTADO. Turnos: "+extractor.turns.size()+". Ganador: "+extractor.winner);
        System.out.println("AST: "+ast+" / Resumen JSON: "+summary+" / Resumen texto: "+resumenTexto);
        return true;
    }
    public static void main(String[] args) throws Exception {
        if(args.length==0){System.err.println("Uso: java Main archivo_log1.txt [archivo_log2.txt ...]");System.exit(2);}
        boolean ok=true;for(String arg:args) if(!analizar(Paths.get(arg)))ok=false;
        if(!ok)System.exit(1);
    }
}
