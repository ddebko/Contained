package com.contained.main.entity;

import net.minecraft.entity.EntityLiving;

public interface CEntityLiving<T extends EntityLiving> extends CEntityLivingBase<T>{
	/*
	 * @return is the entity navigating to a destination
	 */
	public boolean isNavigation();
	
	/*
	 * clear current destination for npc
	 */
	public void clearNavigation();
	
	/*
	 * Set new destination for npc
	 * @param x Destination x position
	 * @param y Destination y position
	 * @param z Destination z position
	 */
	public void navigateTo(double x, double y, double z, double speed);

	@Override
	public T getMCEntity();
}
