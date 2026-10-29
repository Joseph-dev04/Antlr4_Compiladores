import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class ProbarLogs {
    public static void main(String[] args) throws Exception {
        int ok=0;
        String[] validos={"DrCompiler_Log_1.txt","DrCompiler_Log_1_nuevo.txt","DrCompiler_Log_2.txt","DrCompiler_Log_3.txt","DrCompiler_Log_4.txt"};
        for(String file:validos) {
            if(Main.analizar(Paths.get(file))) {System.out.println("OK log válido "+file);ok++;}
            else System.out.println("FALLO log válido "+file);
        }
        String[] invalidos={"pruebas_invalidas/linea_inventada.txt","pruebas_invalidas/sin_cierre.txt"};
        for(String file:invalidos) {
            if(!Main.analizar(Paths.get(file))) {System.out.println("OK rechazo "+file);ok++;}
            else System.out.println("FALLO aceptación indebida "+file);
        }
        System.out.println("TOTAL: "+ok+"/7");
        if(ok!=7)System.exit(1);
    }
}
