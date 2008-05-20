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
package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.GMHennaInfo;
import net.sf.l2j.gameserver.network.serverpackets.GMViewCharacterInfo;
import net.sf.l2j.gameserver.network.serverpackets.GMViewItemList;
import net.sf.l2j.gameserver.network.serverpackets.GMViewPledgeInfo;
import net.sf.l2j.gameserver.network.serverpackets.GMViewQuestInfo;
import net.sf.l2j.gameserver.network.serverpackets.GMViewSkillInfo;
import net.sf.l2j.gameserver.network.serverpackets.GMViewWarehouseWithdrawList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class ...
 * 
 * @version $Revision: 1.1.2.2.2.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestGMCommand extends L2GameClientPacket
{
	private static final String _C__6E_REQUESTGMCOMMAND = "[C] 6e RequestGMCommand";
	static Log _log = LogFactory.getLog(RequestGMCommand.class.getName());
	
	private String _targetName;
	private int _command;
    //private final int _unknown;
	/**
	 * packet type id 0x00
	 * format:	cd
	 *  
	 * @param rawPacket
	 */
    @Override
    protected void readImpl()
    {
        _targetName = readS();
        _command    = readD();
        //_unknown  = readD();
    }

    @Override
    protected void runImpl()
	{
		L2PcInstance player = L2World.getInstance().getPlayer(_targetName);
		L2PcInstance activeChar = getClient().getActiveChar();

		if (player == null || !getClient().getActiveChar().getAccessLevel().allowAltG())
			return;
		
		switch(_command)
		{
			case 1: // player status
			{
				sendPacket(new GMViewCharacterInfo(player));
				sendPacket(new GMHennaInfo(player));
				break;
			}
			case 2: // player clan
			{
				if (player.getClan() != null)
					sendPacket(new GMViewPledgeInfo(player.getClan(), player));
				else
					activeChar.sendMessage(player.getName()+" has no clan.");
				break;
			}
			case 3: // player skills
			{
				sendPacket(new GMViewSkillInfo(player));
				break;
			}
			case 4: // player quests
			{
				sendPacket(new GMViewQuestInfo(player));
				break;
			}
			case 5: // player inventory
			{
				sendPacket(new GMViewItemList(player));
				sendPacket(new GMHennaInfo(player));
				break;
			}
			case 6: // player warehouse
			{
				sendPacket(new GMViewWarehouseWithdrawList(player));
				break;
			}
		}
	}

	/* (non-Javadoc)
	 * @see net.sf.l2j.gameserver.clientpackets.ClientBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _C__6E_REQUESTGMCOMMAND;
	}
}
