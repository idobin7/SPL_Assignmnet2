package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;


    private int tokenCounter;

    private Integer myTokens[];

    private Dealer dealer;

    private Queue<Integer> actions;


    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.tokenCounter = 0;
        this.myTokens = new Integer[env.config.featureSize];
        for (int i = 0; i < myTokens.length; i++) {
            myTokens[i] = -1;
        }
        this.actions = new LinkedList<Integer>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            if (!actions.isEmpty()) {
                int slotAction = actions.remove();
                System.out.println(slotAction);
                if (table.slotToCard[slotAction]!=null) {
                    if (hasToken(slotAction)) {
                        System.out.println("has token");
                        removeToken(slotAction);
                    }
                    else {
                        System.out.println(" not has token");
                        placeToken(slotAction);
                    }
                } else {
                    System.out.println("not in if");
                }
            }
            // TODO implement main player loop
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
         aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random r = new Random();
                int slotAction = r.nextInt(env.config.tableSize);
                System.out.println(slotAction);
                keyPressed(slotAction);
                // TODO implement player key press simulator
                    try {
                        synchronized (this) { wait(); }
                    } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        if (!human)
            aiThread.interrupt();
    }



    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        try {
            synchronized (actions) {
                actions.add(slot);
                actions.notifyAll();
            }
        } catch (IllegalStateException ignored) {}
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        score++;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, score);
        setFreeze(env.config.pointFreezeMillis);
    }

    public void placeToken(int slot) {
        System.out.println("place token");
        if (tokenCounter < env.config.featureSize) {
            boolean isfound = false;
            for (int i = 0; i < myTokens.length && !isfound; i++) {
                if (myTokens[i] == -1) {
                    myTokens[i] = slot;
                    isfound = true;
                }
            }
            tokenCounter++;
            table.placeToken(id, slot);
            if (tokenCounter == env.config.featureSize) {
                boolean isSet;
                //   table.setsDeclared.add(id);
                Integer[] setCopy = new Integer[env.config.featureSize];
                for (int i = 0; i < setCopy.length; i++)
                    setCopy[i] = myTokens[i];
                isSet = dealer.checkSet(setCopy);
                if (isSet)
                    point();
                else {
                    penalty();
                }

            }

        }
    }


    public void removeToken(int slot) {
        boolean found = false;
        for (int i = 0; i < myTokens.length && !found; i++) {
            if (myTokens[i] == slot) {
                myTokens[i] = -1;
                tokenCounter--;
                found = true;
                table.removeToken(id, slot);
            }
        }
    }

    public void deleteTokens() {
        for (int i = 0; i < env.config.featureSize; i++) {
            if (myTokens[i] != -1) {
                table.removeToken(id, myTokens[i]);
                tokenCounter--;
                myTokens[i] = -1;
            }
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        setFreeze(env.config.penaltyFreezeMillis);
    }

    public void setFreeze(long millies) {
        try {
            while (millies > 0) {
                env.ui.setFreeze(id, millies);
                Thread.sleep(millies);
                millies = millies - Table.SECOND_BY_MILLIS;
            }
            env.ui.setFreeze(id, 0);
            unFreeze();
            actions.clear();
        }catch (InterruptedException e){
        }
    }

    public void unFreeze() {
        synchronized (actions) {
            actions.notifyAll();
        }
    }

    public int score() {
        return score;
    }


    public boolean hasToken(int slot) {
        boolean hasToken = false;
        for (int i = 0; i < myTokens.length && !hasToken; i++)
            if (myTokens[i] == slot)
                hasToken = true;
        return hasToken;
    }
}