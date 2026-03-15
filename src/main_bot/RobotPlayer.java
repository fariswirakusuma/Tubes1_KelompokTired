package main_bot;

import battlecode.common.*;
import main_bot.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;


public class RobotPlayer {

    static int turnCount = 0;

    // static double ScorePaint[];
    // static double ScoreBuy[];
    // static double ScoreAttack[];

    static final Random rng = new Random(6147);

    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        System.out.println("I'm alive");

        rc.setIndicatorString("Hello world!");
        
        while (true) {

            turnCount += 1; 

            try {
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc);break;
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                Clock.yield();
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException{
        TowerDecision decision = new TowerDecision(rc);
        while (true){
            try {
                decision.finalDecision();;
                Message[] messages = rc.readMessages(-1);
                for (Message m : messages) {
                    System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
                }
            } catch (Exception e) { e.printStackTrace(); }
            Clock.yield();
           
        }

    }

    public static void runSplasher(RobotController rc) throws GameActionException{
        RobotDecision decision = new RobotDecision(rc, UnitType.SPLASHER);
        while (true) {
            try {
                decision.finalDecision();
            } catch (Exception e) { e.printStackTrace(); }
            Clock.yield();
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException{
        RobotDecision decision = new RobotDecision(rc, UnitType.SOLDIER);
        while (true) {
            try {
                decision.finalDecision();
            } 
            catch (GameActionException e) {
                System.out.println("Tower Action Error");
                e.printStackTrace();
            } 
            catch (Exception e) {
                 e.printStackTrace(); 
                }
            Clock.yield();
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException{
        RobotDecision decision = new RobotDecision(rc, UnitType.MOPPER);
        while (true) {
            try {
                decision.finalDecision();
            } catch (Exception e) { e.printStackTrace(); }
            Clock.yield();
        }
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }
}
