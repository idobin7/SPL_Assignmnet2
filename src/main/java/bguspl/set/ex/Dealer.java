package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
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

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        cardsToRemove=new Integer[3];
        for(int i=0 ;i<cardsToRemove.length;i++){
            cardsToRemove[i]=null;
        }
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
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
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        for(int i=0;i<cardsToRemove.length;i++){
            table.removeCard(cardsToRemove[i]);
        }
        placeCardsOnTable();
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        for(int i=0;i<3 && !deck.isEmpty(); i++){
            table.placeCard(deck.remove(0), cardsToRemove[i]);
            cardsToRemove[i]=null;
         }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset){

        }else {

        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
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

    public boolean checkSet(Integer[] myToken){
        boolean isSetBool;
        int[] isSet = new int[3];
        for (int i = 0; i < 3; i++) {
            isSet[i] = table.slotToCard[myToken[i]];
        }
        isSetBool=env.util.testSet(isSet);
        if(isSetBool){
            cardsToRemove=myToken;
            sucSet();
        }
        return isSetBool;
    }

    public void sucSet(){
        removeCardsFromTable();
        updateTimerDisplay(true);
    }
}
