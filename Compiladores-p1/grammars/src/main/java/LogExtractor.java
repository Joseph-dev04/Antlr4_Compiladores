import java.util.*;
import java.io.PrintStream;
import java.util.regex.*;
import org.antlr.v4.runtime.Token;


/** Listener de ANTLR: crea un AST de acciones y un estado estimado de la partida. */
public class LogExtractor extends PokemonLogBaseListener {
    private static final String P = "[\\p{L}\\p{N}_-]+";
    private static final String A = "['’]s";
    public static class Turn {
        int number;
        String player;
        int damage, kos, prizes, plays, abilities;
        boolean ended;
        final List<Map<String, Object>> events = new ArrayList<>();
        final List<String> attacks = new ArrayList<>();
        public String toString() {
            return "Turno " + number + " [" + player + "] ataques=" + attacks
                + " daño=" + damage + " KOs=" + kos + " premios=" + prizes
                + " cartas jugadas=" + plays + (ended ? "" : " (sin 'ended')");
        }
    }
    public static class PlayerState {
        final String name;
        String active = null;
        final List<String> bench = new ArrayList<>();
        final List<String> knownHand = new ArrayList<>();
        Integer handCount = null; // estimacion a partir de acciones visibles
        int prizesTaken = 0;
        int damageDone = 0;
        int damageReceived = 0;
        int kosInflicted = 0;
        int kosSuffered = 0;
        int energiesAttached = 0;
        int toolsAttached = 0;
        int cardsDrawn = 0;
        int cardsPlayed = 0;
        int turnsPlayed = 0;
        int discardsFromHand = 0;
        int damageHealed = 0;
        int preventedDamageEvents = 0;
        int mulligans = 0;
        int turnTimeouts = 0;
        PlayerState(String name) { this.name = name; }
        Map<String,Object> json() {
            return obj("jugador", name, "pokemonActivo", active, "banca", new ArrayList<>(bench),
                "cartasManoEstimadas", handCount, "cartasConocidasEnMano", new ArrayList<>(knownHand),
                "numeroCartasConocidas", knownHand.size(),
                "cartasSinNombreConocido", handCount == null ? null : Math.max(0, handCount-knownHand.size()),
                "premiosTomados", prizesTaken, "premiosRestantes", Math.max(0,6-prizesTaken),
                "premiosRestantesSiSeEmpiezaConSeis", Math.max(0,6-prizesTaken),
                "danoInfligido", damageDone, "danoRecibido", damageReceived,
                "kosCausados", kosInflicted, "kosRecibidos", kosSuffered,
                "energiasAdjuntadas", energiesAttached, "herramientasAdjuntadas", toolsAttached,
                "cartasRobadas",cardsDrawn,"cartasJugadas",cardsPlayed,"turnosJugados",turnsPlayed,
                "descartesDeMano",discardsFromHand,"danoCurado", damageHealed,
                "eventosDanoPrevenido", preventedDamageEvents,
                "mulligans", mulligans, "turnosSinAccionPorTiempo", turnTimeouts);
        }
        void hand(int delta) { if (handCount != null) handCount = Math.max(0,handCount + delta); }
        void lose(String card) {
            knownHand.remove(card); // Se elimina una copia; puede haber repetidas.
            hand(-1);
        }
    }

    public final List<Turn> turns = new ArrayList<>();
    public final LinkedHashMap<String,PlayerState> players = new LinkedHashMap<>();
    public final List<Map<String,Object>> setup = new ArrayList<>();
    public String winner, winLine;
    private Turn current;
    private String lastListPurpose = "";
    private String lastListPlayer = "";
    private String pendingMulliganDetailPlayer = "";
    private Map<String,Object> lastEvent;
    private PlayerState pendingRemoval;
    private int pendingRemovalCount;
    private String pendingRemovalKind="";
    private String lastDamageSource = "";
    private String lastDamagedPlayer = "";
    private String lastDirectTarget = "";
    private int lastDirectDamage;
    private String stadium;
    private final List<String> limitations = new ArrayList<>(Arrays.asList(
        "La mano es una estimación a partir de eventos visibles; los nombres son solo las cartas conocidas.",
        "En un descarte o barajado sin lista de nombres, se dejan de afirmar identidades que podrían haber salido de la mano.",
        "Los premios restantes se calculan suponiendo seis premios iniciales por jugador.",
        "Pokémon con nombres iguales no pueden distinguirse individualmente solo por el texto del log.",
        "Los KOs sin una fuente de daño atribuible se registran como recibidos, pero no se imputan arbitrariamente a otro jugador."));

    public static Map<String,Object> obj(Object... values) {
        Map<String,Object> m = new LinkedHashMap<>();
        for (int i=0; i<values.length; i+=2) m.put((String) values[i],values[i+1]);
        return m;
    }
    private static Matcher match(String regex,String src) {
        Matcher m=Pattern.compile(regex).matcher(src); return m.matches() ? m : null;
    }
    private static String trim(String t) {return t.replaceAll("[\\r\\n]+$","").trim();}
    private PlayerState state(String name) {return players.computeIfAbsent(name,PlayerState::new);}
    private static String possToPlayer(String text) {return text.replaceFirst("['’]s$", "");}
    private static String firstPlayer(String text) {
        Matcher m = Pattern.compile("^(-\\s*)?("+P+")(?:['’]s)?\\b").matcher(text);
        return m.find()?m.group(2):"";
    }
    private void node(String kind, Token token, boolean isSetup) {
        if (!kind.equals("ListaCartas")) {
            // Si no llegó el detalle del descarte, no sabemos cuáles de las
            // cartas conocidas permanecen todavía en mano.
            finishPendingRemoval();
        }
        if (!kind.equals("ListaCartas") && !kind.equals("DetalleManoInicial") &&
            !kind.equals("DesgloseDano")) {
            lastListPurpose="";
            lastListPlayer="";
        }
        if (!kind.equals("Ataque") && !kind.equals("DesgloseDano") &&
            !kind.equals("ListaCartas") && !kind.equals("DanoPropio")) {
            lastDirectTarget=""; lastDirectDamage=0;
        }
        String line=trim(token.getText());
        Map<String,Object> attrs=new LinkedHashMap<>();
        Map<String,Object> event=obj("node",kind,"linea",token.getLine(),"texto",line,"datos",attrs);
        if(isSetup) setup.add(event); else if(current!=null) current.events.add(event);
        lastEvent=event;
        analyze(kind,line,attrs);
        for(PlayerState p:players.values()) {
            if(p.handCount!=null && p.knownHand.size()>p.handCount)
                p.knownHand.clear(); // no inventar cuáles continúan en mano
        }
    }
    private void finishPendingRemoval() {
        if (pendingRemoval == null) return;
        // Sin desglose de qué cartas salieron, no se puede garantizar que
        // los nombres anteriores continúen en mano.
        pendingRemoval.knownHand.clear();
        if(pendingRemovalKind.equals("shuffle"))pendingRemoval.hand(-pendingRemovalCount);
        pendingRemoval=null; pendingRemovalCount=0; pendingRemovalKind="";
    }
    private void scheduleRemoval(PlayerState player, int amount, String reason) {
        pendingRemoval=player;pendingRemovalCount=amount;pendingRemovalKind=reason;
        lastListPurpose=reason;lastListPlayer=player.name;
    }
    private static boolean isEnergy(String card) {return card.toLowerCase(Locale.ROOT).contains("energy");}
    private static String shown(List<String> values) {
        return values.isEmpty()?"(ninguno conocido)":String.join(", ",values);
    }
    private void trackDamage(String attacker, String victim, int amount, boolean direct) {
        PlayerState target=state(victim);
        target.damageReceived+=amount;
        if(attacker!=null && !attacker.isBlank()) {
            state(attacker).damageDone+=amount;
            if(current!=null)current.damage+=amount;
            lastDamageSource=attacker;lastDamagedPlayer=victim;
        }
        if(direct){lastDirectTarget=victim;lastDirectDamage=amount;}
    }

    @SuppressWarnings("unchecked")
    private void analyze(String kind,String full,Map<String,Object> attrs) {
        String line=full.replaceFirst("^-\\s*", "");
        Matcher m;
        String who=firstPlayer(line);
        if(!who.isEmpty() && !kind.equals("ListaCartas") && !kind.equals("DesgloseDano")
           && !kind.equals("DetalleManoInicial") && !kind.equals("DanoPrevenido")
           && !kind.equals("AgregadoMano") && !kind.equals("Descartar")
           && !kind.equals("CartasMostradasMulligan")) {
            state(who); attrs.put("jugador",who);
        }
        switch(kind) {
            case "ManoInicial":
                m=match("("+P+") drew (\\d+) cards for the opening hand\\.",line);
                if(m!=null){attrs.put("cantidad",Integer.parseInt(m.group(2)));state(m.group(1)).handCount=Integer.parseInt(m.group(2));lastListPlayer=m.group(1);lastListPurpose="initial";}
                break;
            case "Mulligan":
                m=match("("+P+") took a mulligan\\.",line);
                if(m!=null){state(m.group(1)).mulligans++;attrs.put("jugador",m.group(1));}
                break;
            case "CartasMostradasMulligan":
                m=match("Cards revealed from Mulligan (\\d+)",line);
                if(m!=null){attrs.put("numero",Integer.parseInt(m.group(1)));
                    lastListPurpose="mulligan";lastListPlayer="";}
                break;
            case "RoboExtraMulligan":
                m=match("("+P+") drew (\\d+) more cards? because ("+P+") took at least (\\d+) mulligans?\\.",line);
                if(m!=null){int n=Integer.parseInt(m.group(2));
                    state(m.group(1)).hand(n);state(m.group(1)).cardsDrawn+=n;
                    attrs.put("jugador",m.group(1));attrs.put("cantidad",n);
                    attrs.put("causadoPor",m.group(3));
                    pendingMulliganDetailPlayer=m.group(1);
                }
                break;
            case "TiempoAgotado":
                m=match("("+P+") didn't take an action in time\\.",line);
                if(m!=null){state(m.group(1)).turnTimeouts++;
                    attrs.put("jugador",m.group(1));if(current!=null)current.ended=true;}
                break;
            case "Jugada":
                m=match("("+P+") played (.+?)(?: to the (Bench|Active Spot|Stadium spot))?\\.",line);
                if(m!=null){
                    String card=m.group(2),place=m.group(3);attrs.put("carta",card);attrs.put("zona",place);
                    PlayerState s=state(m.group(1));s.lose(card);s.cardsPlayed++;
                    if(current!=null)current.plays++;
                    if("Bench".equals(place))s.bench.add(card);
                    if("Active Spot".equals(place))s.active=card;
                    if("Stadium spot".equals(place))stadium=card;
                }
                break;
            case "Robo":
                m=match("("+P+") drew (a card|\\d+ cards(?: and played them to the Bench)?|.+?)\\.",line);
                if(m!=null){
                    String val=m.group(2);int n=val.equals("a card")?1:val.matches("\\d+ cards.*")?Integer.parseInt(val.split(" ")[0]):1;
                    attrs.put("cantidad",n); if(n==1&&!val.equals("a card"))attrs.put("carta",val);
                    PlayerState s=state(m.group(1));
                    // Tras un robo adicional por mulligan, la línea con "-" solo
                    // revela la identidad de la misma carta: no sumar otra vez.
                    boolean detalleMulligan = full.startsWith("-")
                            && m.group(1).equals(pendingMulliganDetailPlayer);
                    boolean directlyBench=val.contains("played them to the Bench");
                    if(!detalleMulligan) {s.cardsDrawn+=n;if(!directlyBench)s.hand(n);}
                    else {attrs.put("detalleRoboExtraMulligan",true);pendingMulliganDetailPlayer="";}
                    if(n==1&&!val.equals("a card")&&!directlyBench)s.knownHand.add(val);
                    lastListPlayer=s.name;lastListPurpose=directlyBench?"playedBench":"draw";
                }
                break;
            case "Energia":
                m=match("("+P+") attached (.+?) to (.+?) (?:on the Bench|in the Active Spot)\\.",line);
                if(m!=null){attrs.put("energia",m.group(2));attrs.put("pokemon",m.group(3));PlayerState s=state(m.group(1));
                    if(isEnergy(m.group(2)))s.energiesAttached++;else s.toolsAttached++;
                    s.lose(m.group(2));}
                break;
            case "Evolucion":
                m=match("("+P+") evolved (.+?) to (.+?) (?:on the Bench|in the Active Spot)\\.",line);
                if(m!=null){attrs.put("desde",m.group(2));attrs.put("hasta",m.group(3)); PlayerState s=state(m.group(1));s.lose(m.group(3));
                    if(line.contains("in the Active Spot"))s.active=m.group(3);
                    else {s.bench.remove(m.group(2));s.bench.add(m.group(3));}}
                break;
            case "Ataque":
                m=match("("+P+")['’]s (.+?) used (.+?) on ("+P+")['’]s (.+?) for (\\d+) damage\\.(?: (.+?) took (\\d+) more damage because of (.+?) Weakness\\.)?",line);
                if(m!=null){
                    int n=Integer.parseInt(m.group(6));attrs.put("pokemon",m.group(2));attrs.put("ataque",m.group(3));attrs.put("objetivo",m.group(5));attrs.put("defensor",m.group(4));attrs.put("dano",n);
                    trackDamage(m.group(1),m.group(4),n,true);
                    if(m.group(8)!=null){attrs.put("bonificacionDebilidad",Integer.parseInt(m.group(8)));attrs.put("tipoDebilidad",m.group(9));}
                    if(current!=null)current.attacks.add(m.group(3));
                }
                break;
            case "Habilidad":
                m=match("("+P+")['’]s (.+?) used (.+?)\\.",line);
                if(m!=null){attrs.put("pokemon",m.group(2));attrs.put("habilidad",m.group(3));
                    if(current!=null)current.abilities++;}
                break;
            case "KO":
                m=match("("+P+")['’]s (.+?) was Knocked Out!",line);
                if(m!=null){attrs.put("victima",m.group(2));PlayerState victim=state(m.group(1));victim.kosSuffered++;
                    if(Objects.equals(victim.active,m.group(2)))victim.active=null;
                    else victim.bench.remove(m.group(2));
                    if(current!=null)current.kos++;
                    if(lastDamagedPlayer.equals(victim.name) && !lastDamageSource.isEmpty()
                       && !lastDamageSource.equals(victim.name)) {
                        state(lastDamageSource).kosInflicted++;
                        attrs.put("responsableKO",lastDamageSource);
                    } else {
                        attrs.put("responsableKO", "no determinable con certeza");
                    }
                    lastDamagedPlayer=""; lastDamageSource="";}
                break;
            case "Premio":
                m=match("("+P+") took (a Prize card|\\d+ Prize cards)\\.",line);
                if(m!=null){int n=m.group(2).startsWith("a ")?1:Integer.parseInt(m.group(2).split(" ")[0]);
                    attrs.put("cantidad",n); state(m.group(1)).prizesTaken+=n;if(current!=null)current.prizes+=n;}
                break;
            case "FinTurno": if(current!=null)current.ended=true;break;
            case "Retirada":
                m=match("("+P+") retreated (.+?) to the Bench\\.",line);
                if(m!=null){PlayerState s=state(m.group(1));attrs.put("pokemon",m.group(2));if(!s.bench.contains(m.group(2)))s.bench.add(m.group(2));
                    if(Objects.equals(s.active,m.group(2)))s.active=null;}
                break;
            case "PosicionActiva":
                m=match("("+P+")['’]s (.+?) is now in the Active Spot\\.",line);
                if(m!=null){PlayerState s=state(m.group(1));s.active=m.group(2);s.bench.remove(m.group(2));attrs.put("pokemon",m.group(2));}
                break;
            case "Intercambio":
                m=match("("+P+")['’]s (.+?) was switched with ("+P+")['’]s (.+?) to become the Active Pok\\u00E9mon\\.",line);
                if(m!=null){PlayerState s=state(m.group(1)); s.bench.remove(m.group(2));
                    if(!Objects.equals(m.group(2),m.group(4)) && !s.bench.contains(m.group(4)))s.bench.add(m.group(4));
                    s.active=m.group(2);
                    attrs.put("activoNuevo",m.group(2));attrs.put("activoAnterior",m.group(4));}
                break;
            case "Barajar":
                m=match("("+P+") shuffled (.+?) into their deck\\.",line);
                if(m!=null){String cards=m.group(2);int n=cards.matches("\\d+ cards")?Integer.parseInt(cards.split(" ")[0]):1;
                    PlayerState s=state(m.group(1));attrs.put("cantidad",n);
                    if(cards.matches("\\d+ cards"))scheduleRemoval(s,n,"shuffle");
                    else {s.knownHand.remove(cards);s.hand(-1);
                        // Un efecto puede devolver un Pokémon en juego al mazo.
                        if(s.bench.remove(cards))s.hand(1);
                        else if(Objects.equals(s.active,cards)){s.active=null;s.hand(1);}
                    }}
                break;
            case "Descartar":
                m=match("\\d+ cards were discarded from ("+P+")['’]s (.+?)\\.",line);
                if(m!=null){attrs.put("cartasDescartadasDelPokemon",Integer.parseInt(line.split(" ")[0]));attrs.put("pokemon",m.group(2));break;}
                m=match("(.+?) was discarded from ("+P+")['’]s (.+?)\\.",line);
                if(m!=null){attrs.put("carta",m.group(1));attrs.put("pokemon",m.group(3));break;}
                m=match("("+P+") discarded (\\d+ cards|.+?)\\.",line);
                if(m!=null){String v=m.group(2);int n=v.matches("\\d+ cards")?Integer.parseInt(v.split(" ")[0]):1;
                    PlayerState s=state(m.group(1));s.hand(-n);s.discardsFromHand+=n;attrs.put("cantidad",n);
                    if(v.matches("\\d+ cards"))scheduleRemoval(s,n,"discard");
                    else s.knownHand.remove(v);}
                break;
            case "AgregadoMano":
                m=match("(.+?) was added to ("+P+")['’]s hand\\.",line);
                if(m!=null){PlayerState s=state(m.group(2));s.hand(1);
                    if(!m.group(1).equals("A card"))s.knownHand.add(m.group(1));attrs.put("carta",m.group(1));attrs.put("jugador",m.group(2));}
                break;
            case "DesgloseDano":
                lastListPurpose="damage";
                break;
            case "ListaCartas":
                // Reconocer explícitamente los desgloses de daño (no son listas de cartas).
                if (line.matches("•\\s*(Base damage:|Total damage:|Weakness to .+?:|\\(Item\\).+?:)\\s*\\d+ damage")) {
                    Matcher breakdown=Pattern.compile("•\\s*(.+?):\\s*(\\d+) damage").matcher(line);
                    if (breakdown.matches()) {attrs.put("tipoDesglose",breakdown.group(1));attrs.put("dano",Integer.parseInt(breakdown.group(2)));}
                    lastListPurpose="";
                    break;
                }
                // Lista divulgada por el juego: no implica conocer toda la mano.
                String items=line.replaceFirst("^[•]\\s*", "");
                List<String> cards=new ArrayList<>();for(String x:items.split(", "))cards.add(x.trim());
                attrs.put("cartas",cards);
                if((lastListPurpose.equals("initial") || lastListPurpose.equals("draw") ||
                    lastListPurpose.equals("moved")) && !lastListPlayer.isEmpty())
                    state(lastListPlayer).knownHand.addAll(cards);
                if(lastListPurpose.equals("playedBench") && !lastListPlayer.isEmpty())
                    state(lastListPlayer).bench.addAll(cards);
                if((lastListPurpose.equals("shuffle") || lastListPurpose.equals("discard")) &&
                    pendingRemoval!=null) {
                    if(pendingRemovalKind.equals("shuffle")) {
                        // Run Away Draw puede devolver Pokémon de la banca al mazo:
                        // las cartas de la lista no son necesariamente de la mano.
                        boolean fromPlay=false;
                        for(String card:cards)if(pendingRemoval.bench.contains(card)||
                            Objects.equals(pendingRemoval.active,card))fromPlay=true;
                        if(fromPlay) {
                            for(String card:cards) {
                                if(!pendingRemoval.bench.remove(card) &&
                                    Objects.equals(pendingRemoval.active,card))pendingRemoval.active=null;
                            }
                            attrs.put("origenCartas","zona de juego, inferido");
                        } else {
                            for(String card:cards)pendingRemoval.knownHand.remove(card);
                            pendingRemoval.hand(-pendingRemovalCount);
                            attrs.put("origenCartas","mano, inferido");
                        }
                    } else {
                        for(String card:cards)pendingRemoval.knownHand.remove(card);
                    }
                    if(cards.size()!=pendingRemovalCount)pendingRemoval.knownHand.clear();
                    pendingRemoval=null;pendingRemovalCount=0;pendingRemovalKind="";
                }
                lastListPurpose="";lastListPlayer="";
                break;
            case "MoverMano":
                m=match("("+P+") moved ("+P+")['’]s (.+?) to their hand\\.",line);
                if(m!=null){PlayerState s=state(m.group(1));String moved=m.group(3);
                    if(moved.matches("\\d+ cards")){
                        int n=Integer.parseInt(moved.split(" ")[0]);s.hand(n);attrs.put("cantidad",n);
                        lastListPurpose="moved";lastListPlayer=s.name;
                    }else{
                        s.bench.remove(moved);
                        if(Objects.equals(s.active,moved))s.active=null;
                        s.hand(1);s.knownHand.add(moved);attrs.put("carta",moved);
                    }}
                break;
            case "DanoPropio":
                m=match("("+P+")['’]s (.+?) took (\\d+) damage\\.",line);
                if(m!=null){int n=Integer.parseInt(m.group(3));String victim=m.group(1);
                    attrs.put("cantidad",n);attrs.put("pokemon",m.group(2));
                    if(current!=null && !current.player.equals(victim)){
                        // En los ataques con daño explícito, no sumar otra vez el mismo daño.
                        boolean repeated=victim.equals(lastDirectTarget)&&n==lastDirectDamage;
                        if(!repeated){trackDamage(current.player,victim,n,false);
                            attrs.put("fuenteDano",current.player);}
                        else attrs.put("incluidoEnAtaque",true);
                    } else {state(victim).damageReceived+=n;}
                }
                break;
            case "Curacion":
                m=match("("+P+")['’]s (.+?) healed (\\d+) damage\\.",line);
                if(m!=null){int n=Integer.parseInt(m.group(3));state(m.group(1)).damageHealed+=n;
                    attrs.put("pokemon",m.group(2));attrs.put("cantidad",n);}
                break;
            case "DanoPrevenido":
                m=match("Damage to (.+?) was prevented\\.",line);
                if(m!=null){attrs.put("pokemon",m.group(1));
                    // El registro no indica la cantidad evitada. Si conocemos al
                    // propietario del Pokémon por su zona, atribuimos el evento;
                    // de lo contrario queda solo documentado en el AST.
                    String target=m.group(1);
                    for(PlayerState ps:players.values()){
                        if(Objects.equals(ps.active,target)||ps.bench.contains(target)){
                            ps.preventedDamageEvents++;attrs.put("jugador",ps.name);break;
                        }
                    }}
                break;
            default: break;
        }
    }
    private void on(org.antlr.v4.runtime.ParserRuleContext context, String kind, boolean isSetup) {
        node(kind,context.getStart(),isSetup);
    }
    @Override public void enterTurnBlock(PokemonLogParser.TurnBlockContext c) {
        String line=trim(c.TURN_LINE().getText());String player=possToPlayer(line.substring(0,line.length()-5));
        pendingMulliganDetailPlayer="";
        finishPendingRemoval();
        lastDamageSource="";lastDamagedPlayer="";lastDirectTarget="";lastDirectDamage=0;
        current=new Turn();current.number=turns.size()+1;current.player=player;turns.add(current);
        state(player).turnsPlayed++;
    }
    @Override public void enterCoinChoice(PokemonLogParser.CoinChoiceContext c){on(c,"CaraCruz",true);}
    @Override public void enterCoinWinner(PokemonLogParser.CoinWinnerContext c){on(c,"GanadorSorteo",true);}
    @Override public void enterTurnOrder(PokemonLogParser.TurnOrderContext c){on(c,"OrdenTurno",true);}
    @Override public void enterOpeningHand(PokemonLogParser.OpeningHandContext c){on(c,"ManoInicial",true);}
    @Override public void enterMulliganLine(PokemonLogParser.MulliganLineContext c){on(c,"Mulligan",true);}
    @Override public void enterMulliganRevealLine(PokemonLogParser.MulliganRevealLineContext c){on(c,"CartasMostradasMulligan",true);}
    @Override public void enterMulliganExtraDrawLine(PokemonLogParser.MulliganExtraDrawLineContext c){on(c,"RoboExtraMulligan",true);}
    @Override public void enterDrawLine(PokemonLogParser.DrawLineContext c){on(c,"Robo",current==null);}
    @Override public void enterPlayLine(PokemonLogParser.PlayLineContext c){on(c,"Jugada",current==null);}
    @Override public void enterAttachLine(PokemonLogParser.AttachLineContext c){on(c,"Energia",false);}
    @Override public void enterEvolveLine(PokemonLogParser.EvolveLineContext c){on(c,"Evolucion",false);}
    @Override public void enterAttackLine(PokemonLogParser.AttackLineContext c){on(c,"Ataque",false);}
    @Override public void enterAbilityLine(PokemonLogParser.AbilityLineContext c){on(c,"Habilidad",false);}
    @Override public void enterKnockoutLine(PokemonLogParser.KnockoutLineContext c){on(c,"KO",false);}
    @Override public void enterPrizeLine(PokemonLogParser.PrizeLineContext c){on(c,"Premio",false);}
    @Override public void enterEndTurnLine(PokemonLogParser.EndTurnLineContext c){on(c,"FinTurno",false);}
    @Override public void enterTimeoutLine(PokemonLogParser.TimeoutLineContext c){on(c,"TiempoAgotado",false);}
    @Override public void enterSwitchLine(PokemonLogParser.SwitchLineContext c){on(c,"Intercambio",false);}
    @Override public void enterActiveLine(PokemonLogParser.ActiveLineContext c){on(c,"PosicionActiva",false);}
    @Override public void enterRetreatLine(PokemonLogParser.RetreatLineContext c){on(c,"Retirada",false);}
    @Override public void enterShuffleLine(PokemonLogParser.ShuffleLineContext c){on(c,"Barajar",false);}
    @Override public void enterDiscardLine(PokemonLogParser.DiscardLineContext c){on(c,"Descartar",false);}
    @Override public void enterMoveLine(PokemonLogParser.MoveLineContext c){on(c,"MoverMano",false);}
    @Override public void enterHandLine(PokemonLogParser.HandLineContext c){on(c,"AgregadoMano",false);}
    @Override public void enterDamageLine(PokemonLogParser.DamageLineContext c){on(c,"DanoPropio",false);}
    @Override public void enterHealingLine(PokemonLogParser.HealingLineContext c){on(c,"Curacion",false);}
    @Override public void enterPreventionLine(PokemonLogParser.PreventionLineContext c){on(c,"DanoPrevenido",false);}
    @Override public void enterDetail(PokemonLogParser.DetailContext c){
        String kind=c.BULLET_LINE()!=null?"ListaCartas":c.DETAIL_DAMAGE_LINE()!=null?"DesgloseDano":"DetalleManoInicial";
        on(c,kind,current==null);
    }
    @Override public void enterWinnerLine(PokemonLogParser.WinnerLineContext c){
        winLine=trim(c.WINNER_LINE().getText());
        Matcher m=Pattern.compile("("+P+") wins\\.$").matcher(winLine);
        if(m.find())winner=m.group(1);
        finishPendingRemoval();
    }
    public Map<String,Object> ast() {
        List<Object> ts=new ArrayList<>();for(Turn t:turns)ts.add(obj("node","Turno","numero",t.number,"jugador",t.player,"eventos",t.events));
        return obj("node","PartidaPokemon","preparacion",setup,"turnos",ts,
            "ganador",winner,"cierre",winLine);
    }
    public Map<String,Object> summary() {
        List<Object> ts=new ArrayList<>();for(Turn t:turns)ts.add(obj("numero",t.number,"jugador",t.player,"daño",t.damage,"kos",t.kos,"premios",t.prizes,"jugadas",t.plays,"finalizado",t.ended));
        List<Object> ps=new ArrayList<>();for(PlayerState p:players.values())ps.add(p.json());
        finishPendingRemoval();
        return obj("ganador",winner,"cierre",winLine,"numeroTurnos",turns.size(),"turnos",ts,"jugadores",ps,
            "estadioActual",stadium,"limitaciones",new ArrayList<>(limitations));
    }


    /** Muestra en consola todos los campos del apartado avanzado de la práctica. */
    public void printSummary(PrintStream out) {
        finishPendingRemoval();
        out.println("\n========== RESUMEN DE LA PARTIDA ==========");
        out.println("Ganador: " + (winner==null ? "No determinado" : winner));
        out.println("Final: " + (winLine==null ? "No determinado" : winLine));
        out.println("Total de turnos: " + turns.size());
        out.println("Estadio actual: " + (stadium==null ? "No consta" : stadium));
        for(PlayerState p:players.values()) {
            out.println("\n--- JUGADOR: "+p.name+" ---");
            out.println("Turnos jugados: " + p.turnsPlayed);
            out.println("Pokémon activo: " + (p.active==null ? "No identificado" : p.active));
            out.println("Pokémon en banca ("+p.bench.size()+"): " + shown(p.bench));
            out.println("KOs causados: "+p.kosInflicted+" | KOs recibidos: "+p.kosSuffered);
            out.println("Premios tomados: "+p.prizesTaken+" | Premios restantes (de 6): "+Math.max(0,6-p.prizesTaken));
            out.println("Cartas en mano (estimación): " + (p.handCount==null ? "Desconocido" : p.handCount));
            out.println("Cartas identificadas en mano (" +p.knownHand.size()+"): " +shown(p.knownHand));
            if(p.handCount!=null)out.println("Cartas de identidad desconocida: "+Math.max(0,p.handCount-p.knownHand.size()));
            out.println("Daño total infligido: "+p.damageDone+" | Daño recibido: "+p.damageReceived);
            out.println("Daño curado: "+p.damageHealed+" | Efectos de daño prevenido: "+p.preventedDamageEvents);
            out.println("Cartas robadas registradas: "+p.cardsDrawn+" | Cartas jugadas: "+p.cardsPlayed);
            out.println("Energías adjuntadas: "+p.energiesAttached+" | Herramientas adjuntadas: "+p.toolsAttached);
            out.println("Mulligans: "+p.mulligans+" | Tiempo agotado: "+p.turnTimeouts);
        }
        out.println("\n--- RESUMEN POR TURNO ---");
        for(Turn t:turns)out.println(t);
        out.println("\nNOTA: la mano y la banca se deducen del log; cartas ocultas y Pokémon idénticos no se distinguen siempre.");
        out.println("===========================================\n");
    }
}
