import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exporta el AST a JSON y a DOT, sin dependencias adicionales. */
public final class ExportadorAST {
    private ExportadorAST() {}

    public static String json(Object valor) {
        return json(valor, 0) + "\n";
    }

    private static String json(Object valor, int nivel) {
        if (valor == null) return "null";
        if (valor instanceof String) return "\"" + escaparJson((String) valor) + "\"";
        if (valor instanceof Number || valor instanceof Boolean) return valor.toString();
        if (valor instanceof Map<?, ?>) {
            Map<?, ?> mapa = (Map<?, ?>) valor;
            if (mapa.isEmpty()) return "{}";
            List<String> elementos = new ArrayList<>();
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                elementos.add(sangria(nivel + 1) + json(e.getKey().toString(), 0)
                        + ": " + json(e.getValue(), nivel + 1));
            }
            return "{\n" + String.join(",\n", elementos) + "\n" + sangria(nivel) + "}";
        }
        if (valor instanceof List<?>) {
            List<?> lista = (List<?>) valor;
            if (lista.isEmpty()) return "[]";
            List<String> elementos = new ArrayList<>();
            for (Object elem : lista) elementos.add(sangria(nivel + 1) + json(elem, nivel + 1));
            return "[\n" + String.join(",\n", elementos) + "\n" + sangria(nivel) + "]";
        }
        throw new IllegalArgumentException("Elemento de AST no compatible: " + valor.getClass());
    }

    private static String sangria(int nivel) {
        return "  ".repeat(nivel);
    }

    private static String escaparJson(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 32) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
            }
        }
        return out.toString();
    }

    public static String dot(Object raiz) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph AST {\n  rankdir=TB;\n  node [shape=box, fontname=Arial];\n");
        new Dibujante(sb).nodo(raiz, null, null);
        sb.append("}\n");
        return sb.toString();
    }

    private static class Dibujante {
        private final StringBuilder sb;
        private int contador;
        Dibujante(StringBuilder sb) { this.sb = sb; }

        @SuppressWarnings("unchecked")
        void nodo(Object valor, String padre, String arista) {
            if (valor == null) return;
            if (valor instanceof List<?>) {
                List<?> lista = (List<?>) valor;
                for (int i = 0; i < lista.size(); i++) {
                    nodo(lista.get(i), padre, arista + "[" + i + "]");
                }
                return;
            }
            if (!(valor instanceof Map<?, ?>)) return;
            Map<String, Object> mapa = (Map<String, Object>) valor;
            String id = "n" + (++contador);
            StringBuilder etiqueta = new StringBuilder(String.valueOf(mapa.get("node")));
            for (Map.Entry<String, Object> e : mapa.entrySet()) {
                Object v = e.getValue();
                if (!"node".equals(e.getKey()) && v != null && !(v instanceof Map) && !(v instanceof List)) {
                    etiqueta.append("\n").append(e.getKey()).append("=").append(v);
                }
            }
            sb.append("  ").append(id).append(" [label=\"").append(escaparDot(etiqueta.toString()))
                    .append("\"];\n");
            if (padre != null) {
                sb.append("  ").append(padre).append(" -> ").append(id)
                        .append(" [label=\"").append(escaparDot(arista)).append("\"];\n");
            }
            for (Map.Entry<String, Object> e : mapa.entrySet()) {
                if (e.getValue() instanceof List || e.getValue() instanceof Map) {
                    nodo(e.getValue(), id, e.getKey());
                }
            }
        }

        private String escaparDot(String texto) {
            return texto.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "");
        }
    }
}
