package rama_bot;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {

    static int myID = -1;
    static Random rng = null;
    static Direction wanderDir = null;
    static MapLocation targetLoc = null;
    static MapLocation spawnLoc = null;
    static int stuckCount = 0;
    static MapLocation prevLoc = null;

    static final Direction[] dirs = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    static final Direction[] mainDirs = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                if (myID == -1) {
                    myID = rc.getID();
                    rng = new Random(myID);
                    wanderDir = dirs[myID % 8];
                    spawnLoc = rc.getLocation();

                    int w = rc.getMapWidth();
                    int h = rc.getMapHeight();
                    int q = myID % 4;
                    int tx = (q % 2 == 0) ? w / 4 : w * 3 / 4;
                    int ty = (q < 2) ? h / 4 : h * 3 / 4;
                    targetLoc = new MapLocation(tx, ty);
                }

                MapLocation nowLoc = rc.getLocation();
                if (nowLoc.equals(prevLoc)) {
                    stuckCount++;
                } else {
                    stuckCount = 0;
                }
                if (stuckCount >= 3) {
                    wanderDir = dirs[rng.nextInt(8)];
                    stuckCount = 0;
                }
                prevLoc = nowLoc;

                if (rc.getType() == UnitType.SOLDIER) {
                    runSoldier(rc);
                } else if (rc.getType() == UnitType.MOPPER) {
                    runMopper(rc);
                } else if (rc.getType() == UnitType.SPLASHER) {
                    runSplasher(rc);
                } else {
                    runTower(rc);
                }

            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                Clock.yield();
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (int i = 0; i < nearbyEnemies.length; i++) {
            if (rc.canAttack(nearbyEnemies[i].location)) {
                rc.attack(nearbyEnemies[i].location);
                break;
            }
        }

        if (!rc.isActionReady()) return;

        int myChips = rc.getChips();
        int myPaint = rc.getPaint();
        int roundNum = rc.getRoundNum();

        int lowPaint = 0;
        RobotInfo[] allyList = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo a : allyList) {
            if (!a.type.isTowerType() && a.paintAmount < a.type.paintCapacity * 0.30) {
                lowPaint++;
            }
        }
        boolean wantMopper = lowPaint >= 2;

        boolean built = false;

        if (!wantMopper && roundNum < 80) {
            for (Direction d : dirs) {
                MapLocation next = rc.getLocation().add(d);
                if (myChips >= 250 && myPaint >= 100 && rc.canBuildRobot(UnitType.SOLDIER, next)) {
                    rc.buildRobot(UnitType.SOLDIER, next);
                    built = true;
                    break;
                }
            }
        }

        if (!built) {
            for (Direction d : dirs) {
                MapLocation next = rc.getLocation().add(d);
                if (myChips >= 400 && myPaint >= 300 && rc.canBuildRobot(UnitType.SPLASHER, next)) {
                    rc.buildRobot(UnitType.SPLASHER, next);
                    built = true;
                    break;
                }
            }
        }

        if (!built && wantMopper) {
            for (Direction d : dirs) {
                MapLocation next = rc.getLocation().add(d);
                if (myChips >= 300 && myPaint >= 100 && rc.canBuildRobot(UnitType.MOPPER, next)) {
                    rc.buildRobot(UnitType.MOPPER, next);
                    built = true;
                    break;
                }
            }
        }

        if (!built) {
            for (Direction d : dirs) {
                MapLocation next = rc.getLocation().add(d);
                if (myChips >= 250 && myPaint >= 100 && rc.canBuildRobot(UnitType.SOLDIER, next)) {
                    rc.buildRobot(UnitType.SOLDIER, next);
                    break;
                }
            }
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos();

        if (refuelCheck(rc, 0.20)) return;

        MapLocation closestRuin = null;
        int closestDist = 999999;
        for (MapInfo t : tiles) {
            if (!t.hasRuin()) continue;
            MapLocation rl = t.getMapLocation();
            RobotInfo there = rc.senseRobotAtLocation(rl);
            if (there != null && there.type.isTowerType()) continue;
            int dist = rc.getLocation().distanceSquaredTo(rl);
            if (dist < closestDist) {
                closestDist = dist;
                closestRuin = rl;
            }
        }

        if (closestRuin != null) {
            if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin)) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin);
            }

            if (rc.isActionReady()) {
                MapInfo target = null;
                int td = 999999;
                for (MapInfo pt : rc.senseNearbyMapInfos(closestRuin, 8)) {
                    if (pt.getMark() == PaintType.EMPTY) continue;
                    if (pt.getMark() == pt.getPaint()) continue;
                    int dd = rc.getLocation().distanceSquaredTo(pt.getMapLocation());
                    if (dd < td && rc.canAttack(pt.getMapLocation())) {
                        td = dd;
                        target = pt;
                    }
                }
                if (target != null) {
                    boolean sec = target.getMark() == PaintType.ALLY_SECONDARY;
                    rc.attack(target.getMapLocation(), sec);
                }
            }

            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin);
            }
            goTo(rc, closestRuin);
            return;
        }

        if (rc.isActionReady()) {
            MapInfo best = null;
            int bestScore = -1;
            for (MapInfo t : tiles) {
                if (!t.isPassable() || t.getPaint().isAlly()) continue;
                if (!rc.canAttack(t.getMapLocation())) continue;
                int sc = t.getPaint().isEnemy() ? 3 : 1;
                sc -= rc.getLocation().distanceSquaredTo(t.getMapLocation());
                if (sc > bestScore) {
                    bestScore = sc;
                    best = t;
                }
            }
            if (best != null) rc.attack(best.getMapLocation());
        }

        if (rc.isActionReady()) {
            MapInfo here = rc.senseMapInfo(rc.getLocation());
            if (!here.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
                rc.attack(rc.getLocation());
            }
        }

        doWander(rc);
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        if (refuelCheck(rc, 0.20)) return;

        MapInfo[] tiles = rc.senseNearbyMapInfos();

        MapLocation closestRuin = null;
        int closestDist = 999999;
        for (MapInfo t : tiles) {
            if (!t.hasRuin()) continue;
            MapLocation rl = t.getMapLocation();
            RobotInfo there = rc.senseRobotAtLocation(rl);
            if (there != null && there.type.isTowerType()) continue;
            int dist = rc.getLocation().distanceSquaredTo(rl);
            if (dist < closestDist) {
                closestDist = dist;
                closestRuin = rl;
            }
        }

        if (closestRuin != null) {
            if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin)) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin);
            }
            if (rc.isActionReady()) {
                MapInfo splashTarget = null;
                int td = 999999;
                for (MapInfo pt : rc.senseNearbyMapInfos(closestRuin, 8)) {
                    if (pt.getMark() == PaintType.EMPTY) continue;
                    if (pt.getMark() == pt.getPaint()) continue;
                    if (!pt.isPassable()) continue;
                    int dd = rc.getLocation().distanceSquaredTo(pt.getMapLocation());
                    if (dd < td && rc.canAttack(pt.getMapLocation())) {
                        td = dd;
                        splashTarget = pt;
                    }
                }
                if (splashTarget != null) {
                    rc.attack(splashTarget.getMapLocation());
                } else if (rc.canAttack(closestRuin.add(Direction.NORTH))) {
                    rc.attack(closestRuin.add(Direction.NORTH));
                }
            }
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin);
            }
            goTo(rc, closestRuin);
            return;
        }

        if (rc.isActionReady()) {
            MapLocation bestCenter = null;
            int bestCov = 0;
            for (MapInfo t : tiles) {
                if (!t.isPassable()) continue;
                if (!rc.canAttack(t.getMapLocation())) continue;
                int cov = 0;
                for (MapInfo near : rc.senseNearbyMapInfos(t.getMapLocation(), 2)) {
                    if (!near.isPassable() || near.getPaint().isAlly()) continue;
                    cov += near.getPaint().isEnemy() ? 2 : 1;
                }
                if (cov > bestCov) {
                    bestCov = cov;
                    bestCenter = t.getMapLocation();
                }
            }
            if (bestCenter != null && bestCov >= 3) {
                rc.attack(bestCenter);
            } else {
                MapInfo here = rc.senseMapInfo(rc.getLocation());
                if (!here.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
                    rc.attack(rc.getLocation());
                }
            }
        }

        doWander(rc);
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        if (rc.isActionReady()) {
            Direction bestDir = null;
            int bestHits = 0;

            for (Direction d : mainDirs) {
                if (!rc.canMopSwing(d)) continue;
                int hits = 0;
                MapLocation s1 = myLoc.add(d);
                MapLocation s2 = s1.add(d);
                Direction[] around = { d.rotateLeft(), d, d.rotateRight() };
                for (Direction perp : around) {
                    MapLocation p1 = s1.add(perp);
                    MapLocation p2 = s2.add(perp);
                    try {
                        if (rc.onTheMap(p1)) {
                            if (rc.senseMapInfo(p1).getPaint().isEnemy()) hits += 2;
                            RobotInfo ri = rc.senseRobotAtLocation(p1);
                            if (ri != null && ri.team != rc.getTeam()) hits += 3;
                        }
                    } catch (GameActionException e) {}
                    try {
                        if (rc.onTheMap(p2)) {
                            if (rc.senseMapInfo(p2).getPaint().isEnemy()) hits += 2;
                            RobotInfo ri = rc.senseRobotAtLocation(p2);
                            if (ri != null && ri.team != rc.getTeam()) hits += 3;
                        }
                    } catch (GameActionException e) {}
                }
                if (hits > bestHits) {
                    bestHits = hits;
                    bestDir = d;
                }
            }

            if (bestDir != null && bestHits > 0) {
                rc.mopSwing(bestDir);
            } else {
                MapInfo bestTile = null;
                int bestDist = 999999;
                for (MapInfo tile : rc.senseNearbyMapInfos()) {
                    if (!tile.getPaint().isEnemy()) continue;
                    int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                    if (dist < bestDist && rc.canAttack(tile.getMapLocation())) {
                        bestDist = dist;
                        bestTile = tile;
                    }
                }
                if (bestTile != null) rc.attack(bestTile.getMapLocation());
            }
        }

        if (rc.getPaint() > 30) {
            for (RobotInfo a : rc.senseNearbyRobots(2, rc.getTeam())) {
                if (a.type.isTowerType()) continue;
                if (a.paintAmount < a.type.paintCapacity * 0.35 && rc.canTransferPaint(a.location, 30)) {
                    rc.transferPaint(a.location, 30);
                    break;
                }
            }
        }

        if (rc.getPaint() < rc.getType().paintCapacity * 0.40) {
            for (RobotInfo a : rc.senseNearbyRobots(2, rc.getTeam())) {
                if (a.type.isTowerType() && rc.canTransferPaint(a.location, -50)) {
                    rc.transferPaint(a.location, -50);
                    return;
                }
            }
            RobotInfo towerTarget = null;
            int towerDist = 999999;
            for (RobotInfo a : rc.senseNearbyRobots(-1, rc.getTeam())) {
                if (!a.type.isTowerType()) continue;
                int dd = myLoc.distanceSquaredTo(a.location);
                if (dd < towerDist) {
                    towerDist = dd;
                    towerTarget = a;
                }
            }
            if (towerTarget != null) {
                goTo(rc, towerTarget.location);
                if (rc.canTransferPaint(towerTarget.location, -50)) {
                    rc.transferPaint(towerTarget.location, -50);
                }
                return;
            }
        }

        if (rc.isMovementReady()) {
            int[] qScore = new int[4];
            for (MapInfo tile : rc.senseNearbyMapInfos()) {
                if (!tile.getPaint().isEnemy()) continue;
                MapLocation tl = tile.getMapLocation();
                int qx = tl.x >= myLoc.x ? 0 : 1;
                int qy = tl.y >= myLoc.y ? 0 : 2;
                qScore[qx + qy]++;
            }
            int best = 0;
            for (int i = 1; i < 4; i++) {
                if (qScore[i] > qScore[best]) best = i;
            }
            if (qScore[best] > 0) {
                int nx = myLoc.x + (best % 2 == 0 ? 5 : -5);
                int ny = myLoc.y + (best < 2 ? 5 : -5);
                nx = Math.max(0, Math.min(rc.getMapWidth() - 1, nx));
                ny = Math.max(0, Math.min(rc.getMapHeight() - 1, ny));
                goTo(rc, new MapLocation(nx, ny));
            } else {
                int mw = rc.getMapWidth();
                int mh = rc.getMapHeight();
                goTo(rc, new MapLocation(mw - 1 - spawnLoc.x, mh - 1 - spawnLoc.y));
            }
        }
    }

    static boolean refuelCheck(RobotController rc, double threshold) throws GameActionException {
        if (rc.getPaint() >= rc.getType().paintCapacity * threshold) return false;

        for (RobotInfo a : rc.senseNearbyRobots(2, rc.getTeam())) {
            if (a.type.isTowerType() && rc.canTransferPaint(a.location, -100)) {
                rc.transferPaint(a.location, -100);
                return true;
            }
        }

        RobotInfo closest = null;
        int closestD = 999999;
        for (RobotInfo a : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!a.type.isTowerType()) continue;
            int dd = rc.getLocation().distanceSquaredTo(a.location);
            if (dd < closestD) {
                closestD = dd;
                closest = a;
            }
        }
        if (closest != null) {
            goTo(rc, closest.location);
            if (rc.canTransferPaint(closest.location, -100)) {
                rc.transferPaint(closest.location, -100);
            }
            return true;
        }
        return false;
    }

    static void doWander(RobotController rc) throws GameActionException {
        if (!rc.isMovementReady()) return;

        if (rc.getLocation().distanceSquaredTo(targetLoc) <= 9) {
            int mw = rc.getMapWidth();
            int mh = rc.getMapHeight();
            targetLoc = new MapLocation(mw - 1 - targetLoc.x, mh - 1 - targetLoc.y);
        }

        Direction toTarget = rc.getLocation().directionTo(targetLoc);
        Direction pick = (rng.nextInt(5) < 4) ? toTarget : wanderDir;

        if (!tryMove(rc, pick)) {
            for (int i = 0; i < 8; i++) {
                wanderDir = dirs[rng.nextInt(8)];
                if (tryMove(rc, wanderDir)) break;
            }
        }
    }

    static boolean tryMove(RobotController rc, Direction d) throws GameActionException {
        if (!rc.isMovementReady()) return false;
        if (rc.canMove(d)) { rc.move(d); return true; }
        if (rc.canMove(d.rotateLeft())) { rc.move(d.rotateLeft()); return true; }
        if (rc.canMove(d.rotateRight())) { rc.move(d.rotateRight()); return true; }
        return false;
    }

    static void goTo(RobotController rc, MapLocation dest) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction d = rc.getLocation().directionTo(dest);
        if (d == Direction.CENTER) return;
        if (!tryMove(rc, d)) {
            if (rc.canMove(d.rotateLeft().rotateLeft()))
                rc.move(d.rotateLeft().rotateLeft());
            else if (rc.canMove(d.rotateRight().rotateRight()))
                rc.move(d.rotateRight().rotateRight());
        }
    }
}