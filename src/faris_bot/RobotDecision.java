package faris_bot;
import battlecode.common.*;
import java.util.Arrays;

public class RobotDecision {
    private RobotController rc;
    private final UnitType type;

    private int weightPaintEmpty;
    private int weightPaintEnemy;
    private int weightDamageTower;
    private int weightTowerAttack;
    private int weightDistImprove;
    private int weightAllySupport;
    private int weightEnemyThreat;
    private MapLocation lastTargetedTower = null;
    private int towersDestroyedCount = 0;
    private int weightStealPaint;
    private MapLocation explorationTarget = null;
    private int weightTransferPaint;
    private int weightMarking;
    private int weightComm;
    private String actionType;

    private int PAINT_PRIORITY; 
    private int ATTACK_PRIORITY;
    private int SUPPORT_PRIORITY;
    private boolean hasReachedCenter = false;
    private MapLocation[] history = new MapLocation[5];
    private int historyIdx = 0;

    public static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    public RobotDecision(RobotController rc, UnitType type) {
        this.rc = rc;
        this.type = type;
        initializeWeights();
    }

    private void initializeWeights() {
        if (type == UnitType.SOLDIER) {
            this.weightTowerAttack = 800;   
            this.weightDamageTower = 1200;  
            this.weightEnemyThreat = 10;     
            this.weightDistImprove = 25;
            this.PAINT_PRIORITY = 2;
            this.ATTACK_PRIORITY = 50;
            this.SUPPORT_PRIORITY = 10;
        } else if (type == UnitType.MOPPER) {
            this.weightTowerAttack = 100;
            this.weightEnemyThreat = 60;
            this.weightDistImprove = 20; 
            this.weightStealPaint = 500;
            this.weightTransferPaint = 800;
            this.PAINT_PRIORITY = 10;   
            this.ATTACK_PRIORITY = 40;
            this.SUPPORT_PRIORITY = 150;
        } else if (type == UnitType.SPLASHER){
            this.weightTowerAttack = 400;   
            this.weightEnemyThreat = 50;    
            this.weightDistImprove = 15; 
            this.weightPaintEmpty = 500;    
            this.weightPaintEnemy = 600;    
            this.PAINT_PRIORITY = 150;     
            this.ATTACK_PRIORITY = 5;
            this.SUPPORT_PRIORITY = 5;
        }
    }

    private void updateDynamicStrategy() throws GameActionException {
        initializeWeights();
        int round = rc.getRoundNum();
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;

        if (round > 1500) {
            this.PAINT_PRIORITY *= 5; 
            this.weightPaintEmpty += 200;
            this.weightDistImprove += 10;
        }

        if (paintRatio < 0.20) {
            this.PAINT_PRIORITY = 0;
            if (type == UnitType.MOPPER) {
                this.ATTACK_PRIORITY *= 3;
                this.weightStealPaint += 200;
            } else {
                RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
                boolean foundMopper = false;
                for (RobotInfo ally : nearbyAllies) {
                    if (ally.type == UnitType.MOPPER) {
                        foundMopper = true;
                        break;
                    }
                }

                if (!foundMopper) {
                    this.weightDistImprove = 500;
                    this.weightTowerAttack = 2000;
                    for (RobotInfo ally : nearbyAllies) {
                        if (ally.type.isTowerType()) {
                            explorationTarget = ally.location;
                            break;
                        }
                    }
                } else {
                    this.weightDistImprove = 0;
                    this.ATTACK_PRIORITY = 0;
                }
            }
        }

        if (this.towersDestroyedCount > 0) {
            this.weightDistImprove += 50; 
            this.ATTACK_PRIORITY *= 1.5;
            this.weightTowerAttack += 500;
        }

        MapInfo[] tiles = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        int allyTiles = 0;
        for (MapInfo tile : tiles) if (tile.getPaint().isAlly()) allyTiles++;

        if (tiles.length > 0 && (allyTiles * 100 / tiles.length) > 70) {
            this.weightDistImprove *= 2;
            this.PAINT_PRIORITY /= 2;
        }

        if (rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent()).length > 0) {
            if (type == UnitType.SPLASHER) {
                this.weightEnemyThreat = 100;
            } else {
                this.ATTACK_PRIORITY *= 2;
            }
        }
    }

    private void doAction() throws GameActionException {
        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;

        if (type != UnitType.MOPPER && rc.getPaint() < (rc.getType().paintCapacity * 0.2)) {
            RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam());
            for (RobotInfo ally : allies) {
                if (ally.type == UnitType.MOPPER && ally.paintAmount > 50) {
                    return;
                }
            }
        }

        MapLocation[] ruins = rc.senseNearbyRuins(rc.getType().actionRadiusSquared);
        for (MapLocation ruinLoc : ruins) {
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc);
                return; 
            }
            if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc);
                return;
            }
            if (1000 > bestScore) {
                bestScore = 1000;
                bestTarget = ruinLoc;
                actionType = "RUIN_PATTERN";
            }
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam());

        for (RobotInfo enemy : enemies) {
            if (!rc.canAttack(enemy.location)) continue;
            int score = 0;
            if (enemy.getType().isTowerType()) {
                if (rc.getPaint() > (rc.getType().paintCapacity * 0.1)) {
                    score = ActionPower(null, 0, 0, 0, 0, 1, 0, 0, 0); 
                }
            } else if (type == UnitType.MOPPER) {
                int steal = (enemy.paintAmount > 0) ? 1 : 0;
                score = ActionPower(null, 0, 0, 0, steal, 0, 0, 0, 0);
            } else {
                score = ActionPower(null, 0, 0, 0, 0, 0, 0, 0, 0);
            }

            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy.location;
                actionType = "ATTACK";
            }
        }

        for (RobotInfo ally : allies) {
            if (type == UnitType.MOPPER && rc.getPaint() > 20) {
                if (ally.type != UnitType.MOPPER && ally.paintAmount < (ally.type.paintCapacity * 0.8)) {
                    int score = ActionPower(null, 0, 0, 0, 0, 0, 1, 0, 0);
                    if (score > bestScore) {
                        bestScore = score;
                        bestTarget = ally.location;
                        actionType = "TRANSFER";
                    }
                }
            }
        }

        if (bestScore < 50) {
            for (Direction dir : directions) {
                MapLocation tile = rc.getLocation().add(dir);
                if (!rc.canAttack(tile) || !rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (info == null) continue;
                int score = 0;
                if (info.getPaint().isEnemy()) {
                    score = ActionPower(info, 0, 0, 1, 0, 0, 0, 0, 0);
                } else if (info.getPaint() == PaintType.EMPTY) {
                    score = ActionPower(info, 1, 0, 0, 0, 0, 0, 0, 0);
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = tile;
                    actionType = "ATTACK";
                }
            }
        }

        if (bestTarget != null) {
            if (actionType.equals("RUIN_PATTERN")) {
                for (MapInfo patternTile : rc.senseNearbyMapInfos(bestTarget, rc.getType().actionRadiusSquared)) {
                    if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY) {
                        if (rc.canAttack(patternTile.getMapLocation())) {
                            rc.attack(patternTile.getMapLocation(), (patternTile.getMark() == PaintType.ALLY_SECONDARY));
                            return;
                        }
                    }
                }
            } else if (actionType.equals("TRANSFER")) {
                if (rc.canTransferPaint(bestTarget, 20)) rc.transferPaint(bestTarget, 20);
            } else if (rc.canAttack(bestTarget)) { 
                rc.attack(bestTarget);
            }
        }
    }

    public int PositionPower(int tower_opp, int dist_imp, int threat, int ally_count, int ally_paint) {
        double score = 0;
        MapLocation robotLoc = rc.getLocation();

        score += dist_imp * this.weightDistImprove * 20;

        if (tower_opp > 0) score += (tower_opp * this.weightTowerAttack);

        if (dist_imp <= 0) score -= 5000;
        
        score -= (ally_paint * 200);

        if (type == UnitType.MOPPER && ally_count > 0) score += 1500;

        int distToEdgeX = Math.min(robotLoc.x, rc.getMapWidth() - robotLoc.x);
        int distToEdgeY = Math.min(robotLoc.y, rc.getMapHeight() - robotLoc.y);
        if (distToEdgeX < 2 || distToEdgeY < 2) score -= 2000;

        return (int)score;
    }

    public int ActionPower(MapInfo info, int paint_empty, int give_paint, int paint_enemy, int steal_enemy, int damage_tower, int transfer_paint, int marking_value, int communication_value) {
        int score = 0;
        int dpaint_val = (this.weightPaintEnemy * paint_enemy) + (this.weightPaintEmpty * paint_empty);
        int dattack_val = (this.weightDamageTower * damage_tower) + (this.weightStealPaint * steal_enemy);
        int dsupport_val = (this.weightTransferPaint * transfer_paint) + (this.weightMarking * marking_value) + (this.weightComm * communication_value);

        score = (this.PAINT_PRIORITY * dpaint_val) + (this.ATTACK_PRIORITY * dattack_val) + (this.SUPPORT_PRIORITY * dsupport_val);
        
        if (rc.getPaint() < (rc.getType().paintCapacity * 0.4) && steal_enemy > 0) score *= 3;

        return score;
    }

    public Direction chooseMove() throws GameActionException {
        Direction bestDir = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();

        if (explorationTarget == null || myLoc.distanceSquaredTo(explorationTarget) < 4) {
            explorationTarget = new MapLocation(rc.getMapWidth() - myLoc.x, rc.getMapHeight() - myLoc.y);
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam());

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = myLoc.add(dir);

            int allyPaintCount = 0;
            int tileAlreadyFriendlyPenalty = 0;

            for (MapInfo t : nearbyTiles) {
                MapLocation tLoc = t.getMapLocation();
                if (tLoc.equals(next) && t.getPaint().isAlly()) tileAlreadyFriendlyPenalty = 4000;
                if (tLoc.distanceSquaredTo(next) <= 4 && t.getPaint().isAlly()) allyPaintCount++;
            }

            int dist_imp = myLoc.distanceSquaredTo(explorationTarget) - next.distanceSquaredTo(explorationTarget);
            int tower_opp = 0;
            for (RobotInfo e : enemies) if (e.type.isTowerType()) tower_opp = 1;

            int score = PositionPower(tower_opp, dist_imp, 0, allies.length, allyPaintCount) - tileAlreadyFriendlyPenalty;

            for (MapLocation h : history) {
                if (h != null && next.equals(h)) score -= 2500;
            }

            if (dir == Direction.CENTER) score = Integer.MIN_VALUE;

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        history[historyIdx] = myLoc;
        historyIdx = (historyIdx + 1) % history.length;
        return bestDir;
    }

    public void finalDecision() throws GameActionException {
        updateDynamicStrategy();

        if (lastTargetedTower != null && rc.canSenseLocation(lastTargetedTower)) {
            RobotInfo robotAtLoc = rc.senseRobotAtLocation(lastTargetedTower);
            if (robotAtLoc == null || robotAtLoc.team != rc.getTeam().opponent()) {
                MapLocation[] ruins = rc.senseNearbyRuins(rc.getType().actionRadiusSquared);
                for (MapLocation r : ruins) {
                    if (r.equals(lastTargetedTower)) {
                        towersDestroyedCount++;
                        lastTargetedTower = null;
                        break;
                    }
                }
            }
        }

        if (rc.isActionReady()) {
            doAction();
            RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
            for (RobotInfo e : enemies) {
                if (e.type.isTowerType()) {
                    lastTargetedTower = e.location;
                    break;
                }
            }
        }

        if (rc.isMovementReady()) {
            Direction bestDir = chooseMove();
            if (bestDir != Direction.CENTER) rc.move(bestDir);
        }
    }
}