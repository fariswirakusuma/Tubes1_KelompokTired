package alt_bot_1;

import java.util.Random;
import battlecode.common.*;

public class RobotPlayer {

    static int turnCount = 0;
    static Random rng;
    static int towersBuilt = 0;
    static MapLocation knownPaintTower = null;
    static boolean obstacleBlock = false;
    static boolean followRight = true;
    static MapLocation obstacleTarget = null;
    static Direction obstacleDir = Direction.CENTER;

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

        MapInfo here = rc.senseMapInfo(rc.getLocation());
        if (!here.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }

        MapInfo[] nearby = rc.senseNearbyMapInfos();

        MapInfo ruin = null;
        int bestDist = 999999;

        for (MapInfo tile : nearby) {
            if (tile.hasRuin()) {
                int dist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (dist < bestDist) {
                    bestDist = dist;
                    ruin = tile;
                }
            }
        }

        if (ruin != null) {

            MapLocation ruinLoc = ruin.getMapLocation();
            move(rc, ruinLoc, false);

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

                    if (rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation(), sec);
                    }
                }
            }

            if (rc.canCompleteTowerPattern(towerType, ruinLoc)) {
                rc.completeTowerPattern(towerType, ruinLoc);
                towersBuilt++;
            }

            return;
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

        for (RobotInfo r : allies) {
            if (isTowerType(r.type)) {
                MapLocation loc = r.location;
                if (rc.canUpgradeTower(loc)) {
                    rc.upgradeTower(loc);
                    return;
                }
                move(rc, loc, false);
                return;
            }
        }

        Direction dir = directions[rng.nextInt(directions.length)];
        move(rc, rc.getLocation().add(dir), false);
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

                move(rc, tile.getMapLocation(), false);
                return;
            }
        }

        Direction dir = directions[rng.nextInt(directions.length)];
        move(rc, rc.getLocation().add(dir), false);
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

            move(rc, best, false);
            return;
        }

        Direction dir = directions[rng.nextInt(directions.length)];
        move(rc, rc.getLocation().add(dir), false);
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

            move(rc, knownPaintTower, true);

            if (rc.canTransferPaint(knownPaintTower, -100)) {
                rc.transferPaint(knownPaintTower, -100);
            }

            return true;
        }

        return false;
    }

    static void move(RobotController rc, MapLocation target, boolean refuelMode) throws GameActionException {
        if (!rc.isMovementReady()) return;
        MapLocation here = rc.getLocation();
        Direction dirToTarget = here.directionTo(target);

        if (obstacleTarget == null || !obstacleTarget.equals(target)) {
            obstacleBlock = false;
            obstacleTarget = target;
        }

        if (!obstacleBlock) {
            if (rc.canMove(dirToTarget)) {
                rc.move(dirToTarget);
                return;
            }
            obstacleBlock = true;

            Direction left = dirToTarget.rotateLeft();
            Direction right = dirToTarget.rotateRight();
            int leftScore = scoreDirection(rc, left, target);
            int rightScore = scoreDirection(rc, right, target);

            followRight = rightScore < leftScore;
            obstacleDir = followRight ? right : left;
        }

        for (int i = 0; i < 8; i++) {
            if (rc.canMove(obstacleDir)) {
                rc.move(obstacleDir);
                break;
            }
            obstacleDir = followRight ? obstacleDir.rotateRight() : obstacleDir.rotateLeft();
        }

        Direction checkDir = here.directionTo(target);

        if (rc.canMove(checkDir)) {
            Direction sideCheck = followRight ? checkDir.rotateLeft() : checkDir.rotateRight();
            MapLocation sideLoc = here.add(sideCheck);
            MapInfo info = rc.senseMapInfo(sideLoc);
            if (info.isPassable()) {
                obstacleBlock = false;
                obstacleDir = Direction.CENTER;
            }
        }
    }

    static int scoreDirection(RobotController rc, Direction d, MapLocation target) throws GameActionException {
        MapLocation test = rc.getLocation().add(d);
        if (!rc.canMove(d)) return 9999;
        return test.distanceSquaredTo(target);
    }
}