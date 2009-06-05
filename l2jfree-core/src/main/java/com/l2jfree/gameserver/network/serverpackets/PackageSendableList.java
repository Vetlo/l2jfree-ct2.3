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
package com.l2jfree.gameserver.network.serverpackets;

import java.util.List;

import com.l2jfree.Config;
import com.l2jfree.gameserver.model.L2ItemInstance;
import com.l2jfree.gameserver.model.actor.instance.L2PcInstance;

/**
 * @author -Wooden-
 */
public class PackageSendableList extends L2GameServerPacket
{
	private static final String _S__C3_PACKAGESENDABLELIST = "[S] C3 PackageSendableList";
	
	private final List<L2ItemInstance> _items;
	private final int _playerObjId;
	private final long _adena;
	
	public PackageSendableList(L2PcInstance sender, int playerOID)
	{
		_items = sender.getInventory().getAvailableItems(true);
		_playerObjId = playerOID;
		_adena = sender.getAdena();
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.serverpackets.ServerBasePacket#writeImpl()
	 */
	@Override
	protected void writeImpl()
	{
		writeC(0xd2);
		
		writeD(_playerObjId);
		if (Config.PACKET_FINAL)
			writeQ(_adena);
		else
			writeD(toInt(_adena));
		writeD(_items.size());
		for (L2ItemInstance item : _items) // format inside the for taken from SellList part use should be about the same
		{
			writeH(item.getItem().getType1());
			writeD(item.getObjectId());
			writeD(item.getItemDisplayId());
			if (Config.PACKET_FINAL)
				writeQ(item.getCount());
			else
				writeD(toInt(item.getCount()));
			writeH(item.getItem().getType2());
			writeH(item.getCustomType1());
			writeD(item.getItem().getBodyPart());
			writeH(item.getEnchantLevel());
			writeH(item.getCustomType2());
			writeH(0x00);
			writeD(item.getObjectId()); // Will be used in RequestPackageSend response packet
			//T1
			if (Config.PACKET_FINAL)
			{
				writeH(item.getAttackElementType());
				writeH(item.getAttackElementPower());
				for (byte i = 0; i < 6; i++)
				{
					writeH(item.getElementDefAttr(i));
				}
			}
			else
			{
				writeD(item.getAttackElementType());
				writeD(item.getAttackElementPower());
				for (byte i = 0; i < 6; i++)
				{
					writeD(item.getElementDefAttr(i));
				}
			}
		}
	}

	/**
	 * @see com.l2jfree.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__C3_PACKAGESENDABLELIST;
	}
}
