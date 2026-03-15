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
            this.weightTowerAttack = 5000;   
            this.weightDamageTower = 5000;  
            this.weightEnemyThreat = 0;     
            this.weightDistImprove = 80;
            this.PAINT_PRIORITY = 1;
            this.ATTACK_PRIORITY = 200;
            this.SUPPORT_PRIORITY = 0;
        } else if (type == UnitType.MOPPER) {
            this.weightTowerAttack = 1000;
            this.weightEnemyThreat = 5;
            this.weightDistImprove = 50; 
            this.weightStealPaint = 1500;
            this.weightTransferPaint = 2500;
            this.PAINT_PRIORITY = 5;   
            this.ATTACK_PRIORITY = 100;
            this.SUPPORT_PRIORITY = 400;
        } else if (type == UnitType.SPLASHER){
            this.weightTowerAttack = 2000;   
            this.weightEnemyThreat = 0;    
            this.weightDistImprove = 50; 
            this.weightPaintEmpty = 1000;    
            this.weightPaintEnemy = 2000;    
            this.PAINT_PRIORITY = 400;     
            this.ATTACK_PRIORITY = 10;
            this.SUPPORT_PRIORITY = 0;
        }
    }

    private void updateDynamicStrategy() throws GameActionException {
        initializeWeights();
        int currentPaint = rc.getPaint();

        if (currentPaint <= 0) {
            this.PAINT_PRIORITY = 0;
            this.ATTACK_PRIORITY = 0;
            
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
            MapLocation nearestTower = null;
            int minDist = Integer.MAX_VALUE;

            for (RobotInfo ally : nearbyAllies) {
                if (ally.type.isTowerType()) {
                    int dist = rc.getLocation().distanceSquaredTo(ally.location);
                    if (dist < minDist) {
                        minDist = dist;
                        nearestTower = ally.location;
                    }
                }
            }

            if (nearestTower != null) {
                explorationTarget = nearestTower;
                this.weightDistImprove = 20000;
            }
        }
    }

    private void doAction() throws GameActionException {
        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;

        if (type != UnitType.MOPPER && rc.getPaint() <= 0) {
            RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam());
            for (RobotInfo ally : allies) {
                if (ally.type == UnitType.MOPPER && ally.paintAmount > 10) return;
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
            bestScore = 20000;
            bestTarget = ruinLoc;
            actionType = "RUIN_PATTERN";
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam());

        for (RobotInfo enemy : enemies) {
            if (!rc.canAttack(enemy.location)) continue;
            int score = 0;
            if (enemy.getType().isTowerType()) {
                score = ActionPower(null, 0, 0, 0, 0, 1, 0, 0, 0); 
            } else if (type == UnitType.MOPPER) {
                score = ActionPower(null, 0, 0, 0, 1, 0, 0, 0, 0);
            } else if (rc.getPaint() > 0) {
                score = ActionPower(null, 0, 0, 0, 0, 0, 0, 0, 0);
            }

            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy.location;
                actionType = "ATTACK";
            }
        }

        for (RobotInfo ally : allies) {
            if (type == UnitType.MOPPER && rc.getPaint() > 0) {
                if (ally.type != UnitType.MOPPER && ally.paintAmount < ally.type.paintCapacity) {
                    int score = ActionPower(null, 0, 0, 0, 0, 0, 1, 0, 0);
                    if (score > bestScore) {
                        bestScore = score;
                        bestTarget = ally.location;
                        actionType = "TRANSFER";
                    }
                }
            }
        }

        if (bestScore < 1) {
            for (Direction dir : directions) {
                MapLocation tile = rc.getLocation().add(dir);
                if (!rc.canAttack(tile) || !rc.canSenseLocation(tile)) continue;
                MapInfo info = rc.senseMapInfo(tile);
                if (info == null || info.getPaint().isAlly()) continue; 

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
                rc.transferPaint(bestTarget, rc.getPaint());
            } else if (rc.canAttack(bestTarget)) { 
                rc.attack(bestTarget);
            }
        }
    }

    public int PositionPower(int tower_opp, int dist_imp, int ally_paint) {
        double score = 0;
        int multiplier = (rc.getPaint() <= 0) ? 500 : 300; 
        
        if (tower_opp > 0 && rc.getPaint() > 0) {
            score += (tower_opp * this.weightTowerAttack * 10);
            multiplier = 1000; 
        }

        score += dist_imp * this.weightDistImprove * multiplier;
        
        if (dist_imp < 0) score -= 200000; 
        
        if (rc.getPaint() > 0 && tower_opp == 0) {
            score -= (ally_paint * 2000); 
        }
        
        return (int)score;
    }

    public int ActionPower(MapInfo info, int paint_empty, int give_paint, int paint_enemy, int steal_enemy, int damage_tower, int transfer_paint, int marking_value, int communication_value) {
        int dpaint_val = (this.weightPaintEnemy * paint_enemy) + (this.weightPaintEmpty * paint_empty);
        int dattack_val = (this.weightDamageTower * damage_tower) + (this.weightStealPaint * steal_enemy);
        int dsupport_val = (this.weightTransferPaint * transfer_paint);
        return (this.PAINT_PRIORITY * dpaint_val) + (this.ATTACK_PRIORITY * dattack_val) + (this.SUPPORT_PRIORITY * dsupport_val);
    }

    public Direction chooseMove() throws GameActionException {
        Direction bestDir = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();

        
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        MapLocation enemyTower = null;
        for (RobotInfo e : enemies) {
            if (e.type.isTowerType()) {
                enemyTower = e.location;
                break;
            }
        }

        if (enemyTower != null) {
            explorationTarget = enemyTower;
        } else if (explorationTarget == null || (rc.getPaint() > 0 && myLoc.distanceSquaredTo(explorationTarget) < 4)) {
            explorationTarget = new MapLocation(rc.getMapWidth() - myLoc.x, rc.getMapHeight() - myLoc.y);
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = myLoc.add(dir);
            int allyPaintCount = 0;
            int tileAlreadyFriendlyPenalty = 0;

            for (MapInfo t : nearbyTiles) {
                MapLocation tLoc = t.getMapLocation();
                if (tLoc.equals(next) && t.getPaint().isAlly() && enemyTower == null) tileAlreadyFriendlyPenalty = 100000;
                if (tLoc.distanceSquaredTo(next) <= 4 && t.getPaint().isAlly()) allyPaintCount++;
            }

            int dist_imp = myLoc.distanceSquaredTo(explorationTarget) - next.distanceSquaredTo(explorationTarget);
            int tower_opp = (enemyTower != null) ? 1 : 0;

            int score = PositionPower(tower_opp, dist_imp, allyPaintCount) - tileAlreadyFriendlyPenalty;
            if (dir == Direction.CENTER) score = -2000000;

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }
        return bestDir;
    }

    public void finalDecision() throws GameActionException {
        updateDynamicStrategy();
        if (rc.isActionReady()) {
            doAction();
        }
        if (rc.isMovementReady()) {
            Direction bestDir = chooseMove();
            if (bestDir != Direction.CENTER) rc.move(bestDir);
        }
    }
}