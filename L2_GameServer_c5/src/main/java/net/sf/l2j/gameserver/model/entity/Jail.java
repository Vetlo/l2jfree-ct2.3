/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package net.sf.l2j.gameserver.model.entity;

import javolution.util.FastList;
import net.sf.l2j.gameserver.instancemanager.ZoneManager;
import net.sf.l2j.gameserver.model.L2Object;

public class Jail
{
	// =========================================================
    // Data Field
	private int _JailId                       = 0;
    private String _Name                       = "";
    private FastList<int[]> _Spawn;
    private Zone _Zone;

	// =========================================================
	// Constructor
	public Jail(int jailId)
	{
		_JailId = jailId;
        loadData();
	}

	// =========================================================
	// Method - Public
    /** Return true if object is inside the zone */
    public boolean checkIfInZone(L2Object obj) { return checkIfInZone(obj.getX(), obj.getY()); }

    /** Return true if object is inside the zone */
    public boolean checkIfInZone(int x, int y) { return getZone().checkIfInZone(x, y); }
	
	// =========================================================
	// Method - Private
    private void loadData()
    {
        Zone zone = ZoneManager.getInstance().getZone(ZoneType.getZoneTypeName(ZoneType.ZoneTypeEnum.Jail), getJailId());
        if (zone != null) _Name = zone.getName();
    }
	
	// =========================================================
	// Proeprty
	public final int getJailId() { return _JailId; }

    public final String getName() { return _Name; }

    public final FastList<int[]> getSpawn()
    {
        if (_Spawn == null) _Spawn = ZoneManager.getInstance().getZone(ZoneType.getZoneTypeName(ZoneType.ZoneTypeEnum.JailSpawn), getName()).getCoords();
        return _Spawn;
    }

    public final Zone getZone()
    {
        if (_Zone == null) _Zone = ZoneManager.getInstance().getZone(ZoneType.getZoneTypeName(ZoneType.ZoneTypeEnum.Jail), getName());
        return _Zone;
    }
}