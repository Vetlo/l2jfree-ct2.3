/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.handler.items;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * This class ...
 * 
 * @version $Revision: 1.1.2.2.2.7 $ $Date: 2005/04/05 19:41:13 $
 */
public class ScrollOfResurrection implements IItemHandler
{
	// All the item IDs that this handler knows.
	private static final int[] ITEM_IDS = { 737, 3936, 3959, 6387 };
	
	/**
	 * 
	 * @see com.l2jfree.gameserver.handler.IItemHandler#useItem(com.l2jfree.gameserver.gameobjects.L2Playable, com.l2jfree.gameserver.model.items.L2ItemInstance)
	 */
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		if (!(playable instanceof L2Player))
			return;
		
		L2Player activeChar = (L2Player)playable;
		if (activeChar.isSitting())
		{
			activeChar.sendPacket(SystemMessageId.CANT_MOVE_SITTING);
			return;
		}
		if (activeChar.isMovementDisabled())
			return;
		
		int itemId = item.getItemId();
		//boolean blessedScroll = (itemId != 737);
		boolean petScroll = (itemId == 6387);
		
		// SoR Animation section
		L2Creature target = (L2Creature)activeChar.getTarget();
		
		if (target != null && target.isDead())
		{
			L2Player targetPlayer = null;
			
			if (target instanceof L2Player)
				targetPlayer = (L2Player)target;
			
			L2PetInstance targetPet = null;
			
			if (target instanceof L2PetInstance)
				targetPet = (L2PetInstance)target;
			
			if (targetPlayer != null || targetPet != null)
			{
				boolean condGood = true;
				
				//check target is not in a active siege zone
				Castle castle = null;
				
				if (targetPlayer != null)
					castle =
							CastleManager.getInstance().getCastle(targetPlayer.getX(), targetPlayer.getY(),
									targetPlayer.getZ());
				else if (targetPet != null)
					castle =
							CastleManager.getInstance().getCastle(targetPet.getOwner().getX(),
									targetPet.getOwner().getY(), targetPet.getOwner().getZ());
				
				if (castle != null && castle.getSiege().getIsInProgress())
				{
					condGood = false;
					activeChar.sendPacket(SystemMessageId.CANNOT_BE_RESURRECTED_DURING_SIEGE);
				}
				
				if (targetPet != null)
				{
					if (targetPet.getOwner() != activeChar)
					{
						if (targetPet.getOwner().isReviveRequested())
						{
							if (targetPet.getOwner().isReviveRequested())
								activeChar.sendPacket(SystemMessageId.RES_HAS_ALREADY_BEEN_PROPOSED); // Resurrection is already been proposed.
							else
								activeChar.sendPacket(SystemMessageId.CANNOT_RES_PET2); // A pet cannot be resurrected while it's owner is in the process of resurrecting.
							condGood = false;
						}
					}
				}
				else if (targetPlayer != null)
				{
					if (targetPlayer.isFestivalParticipant()) // Check to see if the current player target is in a festival.
					{
						condGood = false;
						activeChar.sendMessage("You may not resurrect participants in a festival.");
					}
					if (targetPlayer.isReviveRequested())
					{
						if (targetPlayer.isReviveRequested())
							activeChar.sendPacket(SystemMessageId.MASTER_CANNOT_RES); // While a pet is attempting to resurrect, it cannot help in resurrecting its master.
						else
							activeChar.sendPacket(SystemMessageId.RES_HAS_ALREADY_BEEN_PROPOSED); // Resurrection is already been proposed.
						condGood = false;
					}
					else if (petScroll)
					{
						condGood = false;
						activeChar.sendMessage("You do not have the correct scroll");
					}
				}
				
				if (condGood)
				{
					if (!activeChar.destroyItem("Consume", item.getObjectId(), 1, null, false))
						return;
					
					int skillId = 0;
					int skillLevel = 1;
					
					switch (itemId)
					{
						case 737:
							skillId = 2014;
							break; // Scroll of Resurrection
						case 3936:
							skillId = 2049;
							break; // Blessed Scroll of Resurrection
						case 3959:
							skillId = 2062;
							break; // L2Day - Blessed Scroll of Resurrection
						case 6387:
							skillId = 2179;
							break; // Blessed Scroll of Resurrection: For Pets
						case 9157:
							skillId = 2321;
							break; // Blessed Scroll of Resurrection Event
						case 10150:
							skillId = 2393;
							break; // Blessed Scroll of Battlefield Resurrection
						case 13259:
							skillId = 2596;
							break; // Gran Kain's Blessed Scroll of Resurrection
					}
					
					if (skillId != 0)
					{
						L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLevel);
						activeChar.useMagic(skill, true, true);
						
						SystemMessage sm = new SystemMessage(SystemMessageId.S1_DISAPPEARED);
						sm.addItemName(item);
						activeChar.sendPacket(sm);
					}
				}
			}
		}
		else
		{
			activeChar.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
		}
	}
	
	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}