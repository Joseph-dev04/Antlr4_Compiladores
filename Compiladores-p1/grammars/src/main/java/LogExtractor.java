import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;
import java.util.*;

public class LogExtractor extends PokemonLogBaseListener {

    public static class Turn {
        int number; String player;
        int damage, kos, prizes, plays; boolean ended;
        List<String> attacks = new ArrayList<>();
        @Override public String toString() {
            return "Turno " + number + " [" + player + "] ataques=" + attacks +
                   " daño=" + damage + " KOs=" + kos + " premios=" + prizes +
                   " cartas jugadas=" + plays + (ended ? "" : " (sin 'ended')");
        }
    }

    public final List<Turn> turns = new ArrayList<>();
    public String winner, winLine;
    private Turn current;

    private static String raw(ParserRuleContext c) {
        return c.start.getInputStream().getText(
            Interval.of(c.start.getStartIndex(), c.stop.getStopIndex()));
    }
    private static String owner(Token t) {            // "DudeJaden’s" -> "DudeJaden"
        String s = t.getText(); return s.substring(0, s.length() - 2);
    }

    @Override public void enterTurnBlock(PokemonLogParser.TurnBlockContext c) {
        current = new Turn();
        current.number = turns.size() + 1;
        current.player = owner(c.player);
        turns.add(current);
    }
    @Override public void enterAttackLine(PokemonLogParser.AttackLineContext c) {
        if (current == null) return;
        current.attacks.add(raw(c.attack));
        current.damage += Integer.parseInt(c.damage.getText());
    }
    @Override public void enterKnockoutLine(PokemonLogParser.KnockoutLineContext c) {
        if (current != null) current.kos++;
    }
    @Override public void enterPrizeLine(PokemonLogParser.PrizeLineContext c) {
        if (current == null) return;
        String n = c.count.getText();
        current.prizes += n.matches("\\d+") ? Integer.parseInt(n) : 1;
    }
    @Override public void enterPlayLine(PokemonLogParser.PlayLineContext c) {
        if (current != null) current.plays++;
    }
    @Override public void enterEndTurnLine(PokemonLogParser.EndTurnLineContext c) {
        if (current != null) current.ended = true;
    }
    @Override public void enterWinnerLine(PokemonLogParser.WinnerLineContext c) {
        winner  = c.winner.getText();
        winLine = raw(c);
    }
}