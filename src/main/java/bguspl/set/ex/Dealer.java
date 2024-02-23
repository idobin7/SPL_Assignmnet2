package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private Integer[] cardsToRemove;

    private long whenToWake = Long.MAX_VALUE;

    private Thread dealerThread;

    public final Object dealerLock;





    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        cardsToRemove=new Integer[env.config.featureSize];
        this.dealerLock = new Object();
        for(int i=0 ;i<cardsToRemove.length;i++){
            cardsToRemove[i]=null;
        }
        Collections.shuffle(deck);

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        this.dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            Thread playerThread = new Thread(player, player.id + " ");
            playerThread.start();
        }
        while (!shouldFinish()) {
            Collections.shuffle(deck);
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            placeCardsOnTable();
            updateTimerDisplay(false);
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        if(!terminate) terminate();
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
      //      synchronized (table) {
                removeCardsFromTable();
                placeCardsOnTable();
      //      }
            while (noSetsAtAll() && !deck.isEmpty()) {
                removeAllCardsFromTable();
                placeCardsOnTable();
                updateTimerDisplay(true);
            }
            if (noSetsAtAll() && deck.isEmpty()) {
                terminate();
                announceWinners();
                env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
            }

        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for (Player player : players)
            player.terminate();
        env.ui.dispose();
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    private boolean noSetsAtAll() {
        List<Integer> slotToCardsToCheck = new LinkedList<Integer>();
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null)
                slotToCardsToCheck.add(table.slotToCard[i]);
        }
        return env.util.findSets(slotToCardsToCheck, 1).isEmpty();
    }


    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        for(int i=0;i<cardsToRemove.length;i++){
            if(cardsToRemove[i] != null){
                int slotToEmpty = cardsToRemove[i];
                List<Integer>[] tmp = table.getTokenMap();
                while(!(tmp[cardsToRemove[i]].isEmpty())){
                    players[tmp[cardsToRemove[i]].get(0)].removeToken(slotToEmpty);
                }
                table.removeCard(cardsToRemove[i]);
                cardsToRemove[i]=null;
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        List<Integer> slots = IntStream.range(0, env.config.tableSize).boxed().collect(Collectors.toList());
        Collections.shuffle(slots);
        for (int slot: slots){
            if(!deck.isEmpty() && table.slotToCard[slot] == null){
                Integer cardToPlace = deck.remove(0);
                table.placeCard(cardToPlace, slot);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            dealerThread.sleep(1);
        } catch (InterruptedException ignored) {}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset && !shouldFinish()) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        } else if (reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis)
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        else
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(),0), true);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (this.table) {
            removeAllTokens();
            List<Integer> slots = IntStream.range(0, env.config.tableSize).boxed().collect(Collectors.toList());
            for (int slot : slots) {
                Integer cardToRemove = table.slotToCard[slot];
                if (cardToRemove != null) {
                    table.removeCard(slot);
                    deck.add(cardToRemove);
                }
            }
            Collections.shuffle(deck);
        }
    }


    public void removeAllTokens() {
        for (Player player : players) {
            player.deleteTokens();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max=-1;
        int counter=0;
        for(int i=0;i< players.length;i++){
            if(players[i].score()>max){
                max=players[i].score();
            }
        }
        for (int i=0;i< players.length;i++){
            if(players[i].score()==max){
                counter++;
            }
        }
        int[] winners=new int[counter];
        int win=0;
        for (int i=0;i< players.length;i++){
            if(players[i].score()==max){
                winners[win]=i;
                win++;
            }
        }
        env.ui.announceWinner(winners);
    }

    public boolean checkSet(Integer[] myToken) {
        boolean isSetBool;
        int[] isSet = new int[env.config.featureSize];
        for (int i = 0; i < env.config.featureSize; i++) {
            isSet[i] = table.slotToCard[myToken[i]];
        }
        isSetBool = env.util.testSet(isSet);
        if (isSetBool) {
            cardsToRemove = myToken;
            sucSet();
        }
        return isSetBool;
    }

    public void sucSet(){
        // removeCardsFromTable();
        updateTimerDisplay(true);
    }
}

