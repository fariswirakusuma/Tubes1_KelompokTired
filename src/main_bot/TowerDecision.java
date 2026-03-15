package main_bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class TowerDecision {
    private final int greatResourceForUpgrade = UnitType.LEVEL_TWO_MONEY_TOWER.moneyCost;
    // private final int IdealSpawnSplasher = 400;
    // private final int IdealSpawnSoldierCount = 2;
    private RobotController rc;
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

    private Direction autoDirection(RobotInfo[] enemies) throws GameActionException {
        if (enemies.length > 0) {
            MapLocation myLoc = rc.getLocation();
            MapLocation closestEnemy = enemies[0].location;
            int minBodyDist = myLoc.distanceSquaredTo(closestEnemy);

            for (RobotInfo enemy : enemies) {
                int dist = myLoc.distanceSquaredTo(enemy.location);
                if (dist < minBodyDist) {
                    minBodyDist = dist;
                    closestEnemy = enemy.location;
                }
            }
            return myLoc.directionTo(closestEnemy);
        }
        int x = rc.getLocation().x;
        int y = rc.getLocation().y;
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();

        boolean isNorth = (y > h / 2);
        boolean isEast = (x > w / 2);
        if (isNorth && isEast) return Direction.SOUTHWEST;
        if (isNorth && !isEast) return Direction.SOUTHEAST;
        if (!isNorth && isEast) return Direction.NORTHWEST;

        return Direction.NORTHEAST;
    }

    public TowerDecision(RobotController rc) {
        this.rc = rc;
    }
    private void Spawn_Robot() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation towerLoc = rc.getLocation();
        int chips = rc.getChips();
        int paint = rc.getPaint();
        int round = rc.getRoundNum();

        int idealSoldiers = (round < 500) ? 3 : (round < 1500 ? 6 : 2);
        int splasherPaintReq = (round > 600) ? 100 : 400;

        int soldiers = 0;
        int moppers = 0;
        int splashers = 0;
        for (RobotInfo a : allies) {
            if (a.type == UnitType.SOLDIER) soldiers++;
            else if (a.type == UnitType.MOPPER) moppers++;
            else if (a.type == UnitType.SPLASHER) splashers++;
        }

        UnitType toBuild = null;

        if (enemies.length > 0) {
            if (enemies[0].type == UnitType.MOPPER) {
                toBuild = UnitType.SOLDIER;
            } else {
                toBuild = UnitType.MOPPER;  
            };
        }
        
        else if ((soldiers > 0 || splashers > 0) && moppers == 0 && chips >= UnitType.MOPPER.moneyCost) {
            toBuild = UnitType.MOPPER;
        }
        else if (chips > 1500 && paint > splasherPaintReq) {
            toBuild = UnitType.SPLASHER;
        }
        else if (soldiers < idealSoldiers && chips >= UnitType.SOLDIER.moneyCost) {
            toBuild = UnitType.SOLDIER;
        }
       
        else if (moppers < ((soldiers + splashers) / 2) && chips >= UnitType.MOPPER.moneyCost) {
            toBuild = UnitType.MOPPER;
        }
        else if (chips >= UnitType.SPLASHER.moneyCost && paint > splasherPaintReq) {
            toBuild = UnitType.SPLASHER;
        }

        if (toBuild == null) return;
        Direction dir = (enemies.length > 0) ? towerLoc.directionTo(enemies[0].location) : autoDirection(enemies);
        
        for (int i = 0; i < 8; i++) {
            MapLocation nextLoc = towerLoc.add(dir);
            if (rc.canBuildRobot(toBuild, nextLoc)) {
                rc.buildRobot(toBuild, nextLoc);
                return;
            }
            dir = dir.rotateLeft();
        }
    }

    public void finalDecision() throws GameActionException {
        if (!rc.isActionReady()) return;
        Attack_Enemy();
        if (rc.canUpgradeTower(rc.getLocation())&&rc.getChips()>greatResourceForUpgrade) {
            rc.upgradeTower(rc.getLocation());
        } else {
            Spawn_Robot();
        }
    }

    private void Attack_Enemy() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        
        if (enemies.length > 0) {
            MapLocation bestTarget = enemies[0].location;
            int minHealth = Integer.MAX_VALUE;
            for (RobotInfo enemy : enemies) {
                if (rc.canAttack(enemy.location) && enemy.health < minHealth) {
                    minHealth = enemy.health;
                    bestTarget = enemy.location;
                }
            }
            
            if (rc.canAttack(bestTarget)) {
                rc.attack(bestTarget);
            }
        }
    }
}
