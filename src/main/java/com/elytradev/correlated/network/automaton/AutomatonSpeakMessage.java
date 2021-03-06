package com.elytradev.correlated.network.automaton;

import com.elytradev.correlated.init.CNetwork;

import com.elytradev.concrete.network.Message;
import com.elytradev.concrete.network.NetworkContext;
import com.elytradev.concrete.network.annotation.field.MarshalledAs;
import com.elytradev.concrete.network.annotation.type.ReceivedOn;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;

@ReceivedOn(Side.CLIENT)
public class AutomatonSpeakMessage extends Message {
	@MarshalledAs("i32")
	public int entityId;
	public String line;

	public AutomatonSpeakMessage(NetworkContext ctx) {
		super(ctx);
	}
	public AutomatonSpeakMessage(int entityId, String line) {
		super(CNetwork.CONTEXT);
		this.entityId = entityId;
		this.line = line;
	}
	
	@Override
	protected void handle(EntityPlayer sender) {
		// TODO Auto-generated method stub
		
	}

}
