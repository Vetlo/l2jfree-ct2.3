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

package com.l2jfree.gameserver.handler.skillhandlers;

import com.l2jfree.gameserver.handler.ISkillHandler;
import com.l2jfree.gameserver.model.L2Character;
import com.l2jfree.gameserver.model.L2Object;
import com.l2jfree.gameserver.model.L2Skill;
import com.l2jfree.gameserver.templates.skills.L2SkillType;

/**
 * @author Forsaiken
 */

public class GiveSp implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS = {L2SkillType.GIVE_SP};

	public void useSkill(@SuppressWarnings("unused") L2Character activeChar, L2Skill skill, L2Object... targets)
	{
		for (L2Object element:  targets)
		{
			if (!(element instanceof L2Character))
				continue;
			
			L2Character target = (L2Character) element;
			
			int spToAdd = (int)skill.getPower();
			target.addExpAndSp(0, spToAdd);
		}
	}

	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}