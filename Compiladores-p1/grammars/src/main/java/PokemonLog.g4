grammar PokemonLog;

// ANALISIS SINTACTICO: cada línea se reconoce mediante un patrón concreto.
gameLog: setupBlock turnBlock+ winnerLine EOF;
setupBlock: SETUP_LINE setupLine*;
setupLine: coinChoice | coinWinner | turnOrder | openingHand | mulliganLine
         | mulliganRevealLine | mulliganExtraDrawLine | drawLine | detail | playLine;
turnBlock: TURN_LINE action*;
action: drawLine | playLine | attachLine | evolveLine | attackLine
      | abilityLine | knockoutLine | prizeLine | endTurnLine | switchLine
      | activeLine | retreatLine | shuffleLine | discardLine | moveLine
      | handLine | damageLine | healingLine | preventionLine | timeoutLine | detail;
coinChoice: COIN_CHOICE_LINE;
coinWinner: COIN_WIN_LINE;
turnOrder: TURN_ORDER_LINE;
openingHand: OPENING_HAND_LINE;
mulliganLine: MULLIGAN_LINE;
mulliganRevealLine: MULLIGAN_REVEAL_LINE;
mulliganExtraDrawLine: MULLIGAN_EXTRA_DRAW_LINE;
drawLine: DRAW_LINE;
playLine: PLAY_LINE;
attachLine: ATTACH_LINE;
evolveLine: EVOLVE_LINE;
attackLine: ATTACK_LINE;
abilityLine: ABILITY_LINE;
knockoutLine: KNOCKOUT_LINE;
prizeLine: PRIZE_LINE;
endTurnLine: END_TURN_LINE;
timeoutLine: TIMEOUT_LINE;
switchLine: SWITCH_LINE;
activeLine: ACTIVE_LINE;
retreatLine: RETREAT_LINE;
shuffleLine: SHUFFLE_LINE;
discardLine: DISCARD_LINE;
moveLine: MOVE_LINE;
handLine: HAND_LINE;
damageLine: SELF_DAMAGE_LINE;
healingLine: HEAL_LINE;
preventionLine: PREVENT_LINE;
detail: DETAIL_DRAWN_LINE | DETAIL_DAMAGE_LINE | BULLET_LINE;
winnerLine: WINNER_LINE;

// ANALISIS LEXICO: las palabras del jugador y las cartas se capturan como datos,
// pero los verbos, lugares y terminaciones son patrones obligatorios.
// Los dos formatos de apóstrofo (' y ’) se aceptan explícitamente.
fragment N: '\r'? '\n' | '\r' | EOF;
fragment SPACE: [ \t]+;
fragment OPT: [ \t]*;
fragment P: [a-zA-Z0-9_\u00C0-\u017F-]+;
fragment POSS: P ('\'' | '’') 's';
fragment TEXT: ~[\r\n]+?;
fragment DIGITS: [0-9]+;
fragment DASH: '-' SPACE;
fragment BUL: OPT '•' SPACE;

SETUP_LINE: 'Setup' N;
TURN_LINE: POSS ' Turn' N;
COIN_CHOICE_LINE: P ' chose ' ('heads'|'tails') ' for the opening coin flip.' N;
COIN_WIN_LINE: P ' won the coin toss.' N;
TURN_ORDER_LINE: P ' decided to go ' ('first'|'second') '.' N;
OPENING_HAND_LINE: P ' drew ' DIGITS ' cards for the opening hand.' N;
DETAIL_DRAWN_LINE: DASH DIGITS ' drawn cards.' N;

// Mulligan: mano sin Pokemon basico, cartas reveladas y robo compensatorio.
MULLIGAN_LINE: P ' took a mulligan.' N;
MULLIGAN_REVEAL_LINE: DASH 'Cards revealed from Mulligan ' DIGITS N;
// Debe preceder a DRAW_LINE: evita clasificar el robo extra como robo normal.
MULLIGAN_EXTRA_DRAW_LINE: P ' drew ' DIGITS ' more ' ('card' | 'cards')
    ' because ' P ' took at least ' DIGITS ' mulligan' 's'? '.' N;

// El orden de las reglas más específicas evita que una habilidad se lea como ataque.
ATTACK_LINE: POSS ' ' TEXT ' used ' TEXT ' on ' POSS ' ' TEXT ' for ' DIGITS ' damage.'
    (' ' POSS ' ' TEXT ' took ' DIGITS ' more damage because of ' TEXT ' Weakness.')? N;
ABILITY_LINE: POSS ' ' TEXT ' used ' TEXT '.' N;
KNOCKOUT_LINE: POSS ' ' TEXT ' was Knocked Out!' N;
PRIZE_LINE: P ' took ' ('a Prize card' | DIGITS ' Prize cards') '.' N;
END_TURN_LINE: P ' ended their turn.' N;
TIMEOUT_LINE: P ' didn\'t take an action in time.' N;

// Cartas jugadas, robadas, energías y evoluciones.
PLAY_LINE: P ' played ' TEXT '.' N;
DRAW_LINE: DASH? P ' drew ' ( 'a card' | DIGITS ' cards' | DIGITS ' cards and played them to the Bench' | TEXT ) '.' N;
ATTACH_LINE: DASH? P ' attached ' TEXT ' to ' TEXT (' on the Bench.' | ' in the Active Spot.') N;
EVOLVE_LINE: DASH? P ' evolved ' TEXT ' to ' TEXT (' on the Bench.' | ' in the Active Spot.') N;
SWITCH_LINE: DASH POSS ' ' TEXT ' was switched with ' POSS ' ' TEXT ' to become the Active Pok\u00E9mon.' N;
ACTIVE_LINE: POSS ' ' TEXT ' is now in the Active Spot.' N;
RETREAT_LINE: P ' retreated ' TEXT ' to the Bench.' N;

// Desgloses de efectos de cartas.
SHUFFLE_LINE: DASH? P ' shuffled ' ( 'their deck' | TEXT ' into their deck') '.' N;
DISCARD_LINE: DASH? ( P ' discarded ' TEXT | DIGITS ' cards were discarded from ' POSS ' ' TEXT | TEXT ' was discarded from ' POSS ' ' TEXT ) '.' N;
MOVE_LINE: DASH P ' moved ' POSS ' ' TEXT ' to their hand.' N;
HAND_LINE: ( 'A card' | TEXT ) ' was added to ' POSS ' hand.' N;
SELF_DAMAGE_LINE: DASH POSS ' ' TEXT ' took ' DIGITS ' damage.' N;
HEAL_LINE: DASH POSS ' ' TEXT ' healed ' DIGITS ' damage.' N;
PREVENT_LINE: DASH 'Damage to ' TEXT ' was prevented.' N;
DETAIL_DAMAGE_LINE: DASH 'Damage breakdown:' N;
BULLET_LINE: BUL TEXT N;

WINNER_LINE: ( 'All Prize cards taken.' | 'Opponent conceded.' | 'Opponent was inactive for too long.' ) SPACE P ' wins.' N;

// Solo descarta espacios entre líneas. Las líneas desconocidas NO se ignoran.
EMPTY_LINE: OPT ( '\r'? '\n' | '\r' ) -> skip;
INVALID_LINE: ~[\r\n]+ N;
