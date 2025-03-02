package be4rjp.shootarian.entity;

import be4rjp.shootarian.Shootarian;
import be4rjp.shootarian.data.settings.Settings;
import be4rjp.shootarian.match.Match;
import be4rjp.shootarian.match.team.ShootarianTeam;
import be4rjp.shootarian.player.ShootarianPlayer;
import be4rjp.shootarian.player.death.PlayerDeathManager;
import be4rjp.shootarian.util.*;
import be4rjp.shootarian.util.particle.BlockParticle;
import be4rjp.shootarian.util.particle.NormalParticle;
import be4rjp.shootarian.util.particle.ShootarianParticle;
import be4rjp.shootarian.weapon.gun.BulletDecay;
import be4rjp.shootarian.weapon.gun.GunWeapon;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class WorldSyncBulletEntity implements ShootarianEntity {
    
    private final Match match;
    private final ShootarianTeam team;
    private Location location;
    private final GunWeapon gunWeapon;
    
    private ShootarianPlayer shooter;
    private Vector direction = new Vector(0, 0, 0);
    private Vector originalDirection = new Vector(0.0, 0.0, 0.0);
    private boolean hitSound = false;
    private boolean hitParticle = false;
    private double bulletSize = 0.2;
    private boolean particle = true;
    private final double G_Var;
    private final BulletDecay bulletDecay;
    
    private boolean isSniperBullet = false;
    
    private int tick = 0;
    private int fallTick = 0;
    
    private boolean remove = false;
    private boolean isDead = false;
    
    private Set<ShootarianPlayer> showPlayer = new HashSet<>();
    
    public WorldSyncBulletEntity(ShootarianTeam team, Location location, GunWeapon gunWeapon){
        this.team = team;
        this.match = team.getMatch();
        this.location = location.clone();
        this.gunWeapon = gunWeapon;
        
        this.G_Var = gunWeapon.getGVariable();
        this.bulletDecay = gunWeapon.getBulletDecay();
    }
    
    
    public void shootInitialize(ShootarianPlayer shooter, Vector direction, int fallTick){
        this.direction = direction.clone();
        this.originalDirection = direction.clone();
        this.shooter = shooter;
        this.fallTick = fallTick;
    }
    
    
    public ShootarianPlayer getShooter(){return this.shooter;}
    
    public boolean isHitParticle() {return hitParticle;}
    
    public boolean isHitSound() {return hitSound;}
    
    public void setHitParticle(boolean hitParticle) {this.hitParticle = hitParticle;}
    
    public void setHitSound(boolean hitSound) {this.hitSound = hitSound;}
    
    public GunWeapon getMainWeapon(){return gunWeapon;}
    
    public double getBulletSize() {return bulletSize;}
    
    public void setBulletSize(double bulletSize) {this.bulletSize = bulletSize;}

    public void setParticle(boolean particle) {this.particle = particle;}
    
    public boolean isSniperBullet() {return isSniperBullet;}
    
    public void setSniperBullet(boolean sniperBullet) {isSniperBullet = sniperBullet;}
    
    @Override
    public void tick() {
    
        if(tick >= 2000 || location.getY() < 0 || remove){
            remove();
            return;
        }
        
        if(Bukkit.isPrimaryThread()){
            baseTick();
        }else{
            Shootarian.getPlugin().getLogger().warning("DO NOT CALL WorldSyncBulletEntity tick(); FROM ASYNC THREAD!");
            TaskHandler.runWorldSync(location.getWorld(), this::baseTick);
        }
        
        tick++;
    }
    
    
    private void baseTick(){
        Location oldLocation = location.clone();
        Vector oldDirection = direction.clone();
    
        Location hitLocation = null;
        
        
        RayTraceResult rayTraceResult = location.getWorld().rayTrace(oldLocation, oldDirection, oldDirection.length(),
                FluidCollisionMode.NEVER, true, bulletSize, entity -> {
                    if(!(entity instanceof LivingEntity)) return false;
                    if(entity == shooter.getBukkitPlayer()) return false;
                    return !PlayerDeathManager.deathPlayer.contains(entity);
                });
        if(rayTraceResult != null){
        
            if(rayTraceResult.getHitBlock() != null || rayTraceResult.getHitEntity() != null){
            
                hitLocation = rayTraceResult.getHitPosition().toLocation(location.getWorld());
            
                if(rayTraceResult.getHitEntity() != null){
                    
                    float damage = gunWeapon.getDamage() * bulletDecay.getRate(tick);
                
                    //エンティティへのヒット
                    Entity hitEntity = rayTraceResult.getHitEntity();
                    if(hitEntity instanceof Player){
                        
                        //ヘッドショット
                        if(hitEntity.getLocation().getY() + 1.3 < hitLocation.getY()) damage = damage * gunWeapon.getHeadShotRate();
    
                        float finalDamage = damage;
                        TaskHandler.runAsync(() -> {
                            ShootarianPlayer target = ShootarianPlayer.getShootarianPlayer((Player) hitEntity);
                            target.giveDamage(finalDamage, shooter, direction, gunWeapon);
                        });
                    }else if(hitEntity instanceof LivingEntity){
                        Player player = shooter.getBukkitPlayer();
                        if(player != null){
                            ((LivingEntity) hitEntity).damage(damage, player);
                            TaskHandler.runSync(() -> ((LivingEntity) hitEntity).setNoDamageTicks(0));
                        }
                    }
                
                } else {
                    //ブロックへのヒット
                    Block hitBlock = rayTraceResult.getHitBlock();
                    Material material = hitBlock.getType();
                    
                    TaskHandler.runAsync(() -> {
                        if(material.toString().contains("GLASS")){
                            match.getBlockUpdater().remove(hitBlock, shooter);
                        }
                    });
                    
                    match.spawnParticle(new BlockParticle(Particle.BLOCK_CRACK, 3, 0, 0, 0, 1, hitBlock.getBlockData()), hitLocation);
                    match.playSound(new ShootarianSound(hitBlock.getSoundGroup().getBreakSound(), 1.0F, 1.0F), hitLocation);
                }
            
                remove = true;
            }
        }
    
        if(tick >= fallTick) {
            double t = ((double) tick / 20.0);
            direction = originalDirection.clone().add(new Vector(0.0, t * t * G_Var, 0.0));
        }
        location.add(direction);
    
        if(particle){
            ShootarianParticle particle;
            if(hitLocation == null) {
                particle = new NormalParticle(Particle.CRIT, 0, direction.getX(), direction.getY(), direction.getZ(), 1);
            }else{
                particle = new NormalParticle(Particle.CRIT, 0, hitLocation.getX() - oldLocation.getX(), hitLocation.getY() - oldLocation.getY(), hitLocation.getZ() - oldLocation.getZ(), 1);
            }
        
            if (isSniperBullet) {
                for(ShootarianPlayer matchPlayer : match.getPlayers()){
                    if(matchPlayer == shooter){
                        matchPlayer.spawnParticleIgnoreRange(particle, oldLocation, Settings.SNIPER_BULLET_ORBIT_PARTICLE);
                    }else{
                        matchPlayer.spawnParticle(particle, oldLocation, Settings.BULLET_ORBIT_PARTICLE);
                    }
                }
            } else {
                match.spawnParticle(particle, oldLocation, Settings.BULLET_ORBIT_PARTICLE);
            }
        }
    }
    
    @Override
    public int getEntityID() {
        return 0;
    }
    
    @Override
    public void spawn() {
        if(shooter.isDeath()) return;
        
        for(ShootarianPlayer shootarianPlayer : match.getPlayers()) {
            Player player = shootarianPlayer.getBukkitPlayer();
            if (player == null) continue;
    
            Location playerLoc = shootarianPlayer.getLocation();
            if (LocationUtil.distanceSquaredSafeDifferentWorld(playerLoc, location) > ENTITY_DRAW_DISTANCE_SQUARE) continue;
            
            showPlayer.add(shootarianPlayer);
        }
        this.tick();
        match.getShootarianEntities().add(this);
    }
    
    @Override
    public void remove() {
        this.isDead = true;
        match.getShootarianEntities().remove(this);
    }
    
    @Override
    public boolean isDead() {
        return this.isDead;
    }
}
