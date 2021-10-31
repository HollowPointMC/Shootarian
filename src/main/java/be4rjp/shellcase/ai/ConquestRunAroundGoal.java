package be4rjp.shellcase.ai;

import be4rjp.shellcase.match.ConquestMatch;
import be4rjp.shellcase.match.Match;
import be4rjp.shellcase.player.ShellCasePlayer;
import be4rjp.shellcase.util.SphereBlocks;
import net.citizensnpcs.api.ai.GoalSelector;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ConquestRunAroundGoal extends AIGoal<ConquestMatch> {
    
    private final ShootGunGoal<ConquestMatch> shootGunGoal;
    
    private final NavigationHelper navigationHelper = new NavigationHelper();
    
    public ConquestRunAroundGoal(ShellCasePlayer shellCasePlayer, ConquestMatch conquestMatch, AILevel aiLevel) {
        super(shellCasePlayer, conquestMatch, aiLevel);
        
        this.shootGunGoal = new ShootGunGoal<>(shellCasePlayer, conquestMatch, aiLevel);
    }
    
    @Override
    public void reset() {
    
    }
    
    @Override
    public void run(GoalSelector goalSelector) {
        if(match.getMatchStatus() != Match.MatchStatus.IN_PROGRESS) return;
    
        if(navigationHelper.tick(goalSelector, npc, aiShellCasePlayer, aiLevel)) return;
        
        Set<ShellCasePlayer> targetPlayers = match.getPlayersInRange(aiShellCasePlayer.getLocation(), aiLevel.getEnemyFindRange());
        targetPlayers.removeIf(target -> target.getShellCaseTeam() == aiShellCasePlayer.getShellCaseTeam());
        
        if(targetPlayers.size() != 0){
            List<ShellCasePlayer> targetList = new ArrayList<>(targetPlayers);
            ShellCasePlayer target = null;
            for(ShellCasePlayer shellCasePlayer : targetList){
                if(AIManager.peek(aiShellCasePlayer, shellCasePlayer)){
                    target = shellCasePlayer;
                    break;
                }
            }
            
            if(target == null){
                aiShellCasePlayer.setAiTarget(null);
            } else {
                if (!target.isOnline() || target.isDeath()) {
                    aiShellCasePlayer.setAiTarget(null);
                } else {
                    aiShellCasePlayer.setAiTarget(target);
                }
            }
        }
        
        if(aiShellCasePlayer.getAiTarget() == null){
            if(!npc.getNavigator().isNavigating()){
                SphereBlocks sphereBlocks = new SphereBlocks(aiLevel.getEnemyFindRange(), aiShellCasePlayer.getLocation());
                Set<Block> blocks = sphereBlocks.getBlocks();
                blocks.removeIf(block -> block.getType() == Material.AIR || block.getRelative(0, 1, 0).getType() != Material.AIR || block.getRelative(0, 2, 0).getType() != Material.AIR);
                if(blocks.size() == 0) return;
                List<Block> blockList = new ArrayList<>(blocks);
                Block target = blockList.get(new Random().nextInt(blockList.size())).getRelative(BlockFace.UP);
                
                npc.faceLocation(target.getLocation());
                npc.getNavigator().setTarget(target.getLocation());
            }
        }
    }
    
    @Override
    public boolean shouldExecute(GoalSelector goalSelector) {
        return aiShellCasePlayer.getAiTarget() == null;
    }
    
}
