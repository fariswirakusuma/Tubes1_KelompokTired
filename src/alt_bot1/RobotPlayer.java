package alt_bot1;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {

    static int turnCount = 0;
    static Random rng;

    static final Direction[] directions = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST,
            Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST,
            Direction.WEST, Direction.NORTHWEST,
    };

    static int soldierProduced = 0;
    static boolean ruinFoundGlobal = true;

    public static void run(RobotController rc) throws GameActionException {

        rng = new Random(rc.getID());

        while (true) {

            turnCount++;

            try {

                switch (rc.getType()) {
                    case SOLDIER: runSoldier(rc); break;
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break;
                    default: runTower(rc); break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {

        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation spawn = rc.getLocation().add(dir);

        if (!rc.isActionReady()) return;

        if (turnCount < 200) {

            if (rc.canBuildRobot(UnitType.SOLDIER, spawn)) {
                rc.buildRobot(UnitType.SOLDIER, spawn);
                soldierProduced++;
                return;
            }
        }

        if (ruinFoundGlobal == false) {

            if (rng.nextBoolean()) {

                if (rc.canBuildRobot(UnitType.MOPPER, spawn)) {
                    rc.buildRobot(UnitType.MOPPER, spawn);
                    return;
                }

            } else {

                if (rc.canBuildRobot(UnitType.SPLASHER, spawn)) {
                    rc.buildRobot(UnitType.SPLASHER, spawn);
                    return;
                }
            }
        }


        if (rc.canBuildRobot(UnitType.SPLASHER, spawn)) {
            rc.buildRobot(UnitType.SPLASHER, spawn);
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException {

        MapInfo[] nearby = rc.senseNearbyMapInfos();

        MapInfo ruin = null;

        for (MapInfo tile : nearby) {
            if (tile.hasRuin()) {
                ruin = tile;
                ruinFoundGlobal = true;
                break;
            }
        }

        if (ruin != null) {

            MapLocation ruinLoc = ruin.getMapLocation();
            Direction dir = rc.getLocation().directionTo(ruinLoc);

            if (rc.canMove(dir)) rc.move(dir);

            if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc);
            }

            for (MapInfo tile : rc.senseNearbyMapInfos(ruinLoc, 8)) {

                if (tile.getMark() != tile.getPaint()
                        && tile.getMark() != PaintType.EMPTY) {

                    boolean sec = tile.getMark() == PaintType.ALLY_SECONDARY;

                    if (rc.canAttack(tile.getMapLocation()))
                        rc.attack(tile.getMapLocation(), sec);
                }
            }

            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc);
            }

            return;
        }

        ruinFoundGlobal = false;

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

        for (RobotInfo r : allies) {

            if (r.getType().isTower()) {

                if (rc.canUpgradeTower(r.getLocation())) {
                    rc.upgradeTower(r.getLocation());
                    return;
                }

                Direction dir = rc.getLocation().directionTo(r.getLocation());
                if (rc.canMove(dir)) rc.move(dir);
                return;
            }
        }

        if (rc.getPaint() < rc.getType().paintCapacity * 0.3) {

            for (RobotInfo r : allies) {
                if (r.getType().isTower()) {

                    Direction dir = rc.getLocation().directionTo(r.getLocation());
                    if (rc.canMove(dir)) rc.move(dir);
                    return;
                }
            }
        }

        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) rc.move(dir);
    }

    public static void runMopper(RobotController rc) throws GameActionException {

        MapInfo[] tiles = rc.senseNearbyMapInfos();

        for (MapInfo tile : tiles) {

            if (tile.getPaint().isEnemy()) {

                if (rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation());
                    return;
                }

                Direction dir = rc.getLocation().directionTo(tile.getMapLocation());
                if (rc.canMove(dir)) rc.move(dir);

                return;
            }
        }

        Direction dir = directions[rng.nextInt(directions.length)];

        if (rc.canMove(dir)) rc.move(dir);
    }

    public static void runSplasher(RobotController rc) throws GameActionException {

        MapInfo[] tiles = rc.senseNearbyMapInfos();

        MapLocation best = null;
        int bestScore = -999;

        for (MapInfo tile : tiles) {

            int score = 0;

            if (tile.getPaint().isEnemy()) score += 5;
            if (tile.getPaint() == PaintType.EMPTY) score += 3;

            int dist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
            score -= dist;

            if (score > bestScore) {
                bestScore = score;
                best = tile.getMapLocation();
            }
        }

        if (best != null) {

            if (rc.canAttack(best)) {
                rc.attack(best);
                return;
            }

            Direction dir = rc.getLocation().directionTo(best);
            if (rc.canMove(dir)) rc.move(dir);
        }
    }

}