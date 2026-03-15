package alt_bot1;

import java.util.Random;
import battlecode.common.*;

public class RobotPlayer {

    static int turnCount = 0;
    static Random rng;
    static int towersBuilt = 0;
    static MapLocation knownPaintTower = null;

    static final Direction[] directions = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST,
            Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST,
            Direction.WEST, Direction.NORTHWEST,
    };

    static boolean isTowerType(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
                || type == UnitType.LEVEL_TWO_PAINT_TOWER
                || type == UnitType.LEVEL_THREE_PAINT_TOWER
                || type == UnitType.LEVEL_ONE_MONEY_TOWER
                || type == UnitType.LEVEL_TWO_MONEY_TOWER
                || type == UnitType.LEVEL_THREE_MONEY_TOWER;
    }

    public static void run(RobotController rc) throws GameActionException {

        if (rng == null) rng = new Random(rc.getID());

        while (true) {

            turnCount++;

            try {
                updateNearestPaintTower(rc);

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

        if (!rc.isActionReady()) return;

        UnitType spawnType;

        if (turnCount < 200) {
            spawnType = UnitType.SOLDIER;
        } else {
            spawnType = rng.nextBoolean() ? UnitType.MOPPER : UnitType.SPLASHER;
        }

        for (Direction dir : directions) {

            MapLocation spawn = rc.getLocation().add(dir);

            if (rc.canBuildRobot(spawnType, spawn)) {
                rc.buildRobot(spawnType, spawn);
                return;
            }
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException {

        if (refuelPaint(rc)) return;

        MapInfo[] nearby = rc.senseNearbyMapInfos();

        MapInfo ruin = null;

        for (MapInfo tile : nearby) {
            if (tile.hasRuin()) {
                ruin = tile;
                break;
            }
        }

        if (ruin != null) {

            MapLocation ruinLoc = ruin.getMapLocation();
            Direction dir = rc.getLocation().directionTo(ruinLoc);

            if (rc.canMove(dir)) {
                rc.move(dir);
            }

            UnitType towerType;

            if (towersBuilt % 2 == 0) {
                towerType = UnitType.LEVEL_ONE_PAINT_TOWER;
            } else {
                towerType = UnitType.LEVEL_ONE_MONEY_TOWER;
            }

            if (rc.canMarkTowerPattern(towerType, ruinLoc)) {
                rc.markTowerPattern(towerType, ruinLoc);
            }

            for (MapInfo tile : rc.senseNearbyMapInfos(ruinLoc, 8)) {

                if (tile.getMark() != tile.getPaint()
                        && tile.getMark() != PaintType.EMPTY) {

                    boolean sec = tile.getMark() == PaintType.ALLY_SECONDARY;

                    if (rc.canAttack(tile.getMapLocation()))
                        rc.attack(tile.getMapLocation(), sec);
                }
            }

            if (rc.canCompleteTowerPattern(towerType, ruinLoc)) {
                rc.completeTowerPattern(towerType, ruinLoc);
                towersBuilt++;
            }

            return;
        }

        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) rc.move(dir);
    }

    public static void runMopper(RobotController rc) throws GameActionException {

        if (refuelPaint(rc)) return;

        MapInfo[] tiles = rc.senseNearbyMapInfos();

        for (MapInfo tile : tiles) {

            if (tile.getPaint().isEnemy()) {

                Direction dir = rc.getLocation().directionTo(tile.getMapLocation());

                if (rc.canMopSwing(dir)) {
                    rc.mopSwing(dir);
                    return;
                }

                if (rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation());
                    return;
                }

                if (rc.canMove(dir)) {
                    rc.move(dir);
                    return;
                }
            }
        }

        Direction dir = directions[rng.nextInt(directions.length)];

        if (rc.canMove(dir)) rc.move(dir);
    }

    public static void runSplasher(RobotController rc) throws GameActionException {

        if (refuelPaint(rc)) return;

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

            if (rc.canMove(dir)) {
                rc.move(dir);
                return;
            }
        }

        Direction dir = directions[rng.nextInt(directions.length)];

        if (rc.canMove(dir)) rc.move(dir);
    }

    static void updateNearestPaintTower(RobotController rc) throws GameActionException {

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

        for (RobotInfo r : allies) {

            if (r.type == UnitType.LEVEL_ONE_PAINT_TOWER
                    || r.type == UnitType.LEVEL_TWO_PAINT_TOWER
                    || r.type == UnitType.LEVEL_THREE_PAINT_TOWER) {

                if (knownPaintTower == null) {
                    knownPaintTower = r.location;
                } else {

                    int oldDist = rc.getLocation().distanceSquaredTo(knownPaintTower);
                    int newDist = rc.getLocation().distanceSquaredTo(r.location);

                    if (newDist < oldDist) {
                        knownPaintTower = r.location;
                    }
                }
            }
        }
    }

    static boolean refuelPaint(RobotController rc) throws GameActionException {

        if (rc.getPaint() > rc.getType().paintCapacity * 0.25) {
            return false;
        }

        if (knownPaintTower != null) {

            Direction dir = rc.getLocation().directionTo(knownPaintTower);

            if (rc.canMove(dir)) {
                rc.move(dir);
            }

            if (rc.canTransferPaint(knownPaintTower, -100)) {
                rc.transferPaint(knownPaintTower, -100);
            }

            return true;
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

        for (RobotInfo r : allies) {

            Direction dir = rc.getLocation().directionTo(r.location);

            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
        }

        return false;
    }
}