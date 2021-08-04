package be4rjp.shellcase.weapon.gadget;

import be4rjp.shellcase.player.ShellCasePlayer;
import be4rjp.shellcase.weapon.WeaponManager;
import be4rjp.shellcase.weapon.WeaponStatusData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GadgetStatusData extends WeaponStatusData {

    private final GadgetWeapon gadgetWeapon;
    private final ShellCasePlayer shellCasePlayer;

    private int bullets = 5;
    private boolean isReloading = false;

    //弾数の動作の同期用インスタンス
    private final Object BULLETS_LOCK = new Object();

    public GadgetStatusData(GadgetWeapon gadgetWeapon, ShellCasePlayer shellCasePlayer){
        super(gadgetWeapon, shellCasePlayer);
        this.gadgetWeapon = gadgetWeapon;
        this.shellCasePlayer = shellCasePlayer;
    }

    @Override
    public void updateDisplayName(ShellCasePlayer shellCasePlayer){
        Player player = shellCasePlayer.getBukkitPlayer();
        if(player == null) return;

        for(int index = 0; index < 9; index++){
            ItemStack itemStack = player.getInventory().getItem(index);
            if(itemStack == null) continue;

            GadgetWeapon gadgetWeapon = WeaponManager.getGadgetWeaponByItem(itemStack);
            if(gadgetWeapon == null) continue;
            if(gadgetWeapon != this.gadgetWeapon) continue;

            player.getInventory().setItem(index, this.getItemStack(shellCasePlayer.getLang()));
            break;
        }
    }
}
