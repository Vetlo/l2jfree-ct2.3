/* This program is free software; you can redistribute it and/or modify
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
package net.sf.l2j.status;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.l2j.Config;
import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.Announcements;
import net.sf.l2j.gameserver.GameTimeController;
import net.sf.l2j.gameserver.LoginServerThread;
import net.sf.l2j.gameserver.Shutdown;
import net.sf.l2j.gameserver.ThreadPoolManager;
import net.sf.l2j.gameserver.cache.HtmCache;
import net.sf.l2j.gameserver.clientpackets.Say2;
import net.sf.l2j.gameserver.datatables.GmListTable;
import net.sf.l2j.gameserver.datatables.ItemTable;
import net.sf.l2j.gameserver.datatables.NpcTable;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.datatables.TeleportLocationTable;
import net.sf.l2j.gameserver.datatables.TradeListTable;
import net.sf.l2j.gameserver.instancemanager.IrcManager;
import net.sf.l2j.gameserver.instancemanager.Manager;
import net.sf.l2j.gameserver.instancemanager.SiegeManager;
import net.sf.l2j.gameserver.instancemanager.ZoneManager;
import net.sf.l2j.gameserver.model.L2Character;
import net.sf.l2j.gameserver.model.L2ItemInstance;
import net.sf.l2j.gameserver.model.L2Multisell;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2Summon;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.TradeList;
import net.sf.l2j.gameserver.model.TradeList.TradeItem;
import net.sf.l2j.gameserver.model.actor.instance.L2DoorInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2MonsterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2NpcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.L2GameClient;
import net.sf.l2j.gameserver.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.serverpackets.InventoryUpdate;
import net.sf.l2j.gameserver.serverpackets.LeaveWorld;
import net.sf.l2j.gameserver.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.taskmanager.DecayTaskManager;
import net.sf.l2j.gameserver.util.DynamicExtension;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class GameStatusThread extends Thread
{
    private static final Log _log = LogFactory.getLog(GameStatusThread.class.getName());
    
    private Socket                  _cSocket;
    
    private PrintWriter             _print;
    private BufferedReader          _read;
    
    private int                     _uptime;
      
    private void telnetOutput(int type, String text) 
    {
    	if (Config.DEVELOPER)
    	{
    		if ( type == 1 ) System.out.println("TELNET | "+text);
    		else if ( type == 2 ) System.out.print("TELNET | "+text);
    		else if ( type == 3 ) System.out.print(text);
    		else if ( type == 4 ) System.out.println(text);
    		else System.out.println("TELNET | "+text);
    	}
    	else
    	{
    		//only print output if the message is rejected
    		if ( type == 5 ) System.out.println("TELNET | "+text);
    	}
    }
    
    private boolean isValidIP(Socket client) {
        boolean result = false;        
        InetAddress ClientIP = client.getInetAddress();
               
        // convert IP to String, and compare with list
        String clientStringIP = ClientIP.getHostAddress();
        
        telnetOutput(1, "Connection from: "+clientStringIP);
        
        // read and loop thru list of IPs, compare with newIP
        if ( Config.DEVELOPER ) telnetOutput(2, "");    
        
        try {
            Properties telnetSettings = new Properties();
            InputStream telnetIS = new FileInputStream(new File(Config.TELNET_FILE));
            telnetSettings.load(telnetIS);
            telnetIS.close();
            
            String HostList = telnetSettings.getProperty("ListOfHosts", "127.0.0.1,localhost");
            
            if ( Config.DEVELOPER ) telnetOutput(3, "Comparing ip to list...");
            
            // compare
            String ipToCompare = null;
            for (String ip:HostList.split(",")) {
                if ( !result ) {
                    ipToCompare = InetAddress.getByName(ip).getHostAddress();
                    if ( clientStringIP.equals(ipToCompare) ) result = true;
                    if ( Config.DEVELOPER ) telnetOutput(3, clientStringIP + " = " + ipToCompare + "("+ip+") = " + result);
                }
            }    
        }
        catch ( IOException e) {
            if ( Config.DEVELOPER ) telnetOutput(4, "");
            telnetOutput(1, "Error: "+e);
        }
        
        if ( Config.DEVELOPER ) telnetOutput(4, "Allow IP: "+result);
        return result;
    }
    
    public GameStatusThread(Socket client, int uptime, String StatusPW) throws IOException
    {
        _cSocket = client;
        _uptime = uptime;
        
        _print = new PrintWriter(_cSocket.getOutputStream());
        _read  = new BufferedReader(new InputStreamReader(_cSocket.getInputStream()));
        
        if ( isValidIP(client) ) {    
            telnetOutput(1, client.getInetAddress().getHostAddress()+" accepted.");
            _print.println("Welcome To The L2J Telnet Session.");
            _print.println("Please Insert Your Password!");
            _print.print("Password: ");
            _print.flush();
            String tmpLine = _read.readLine();
            if ( tmpLine == null )  {
                _print.println("Error.");
                _print.println("Disconnected...");
                _print.flush();
                _cSocket.close();
            }
            else {
                if (tmpLine.compareTo(StatusPW) != 0)
                {
                    _print.println("Incorrect Password!");
                    _print.println("Disconnected...");
                    _print.flush();
                    _cSocket.close();
                }
                else
                {
                    _print.println("Password Correct!");
                    _print.println("[L2J]");
                    _print.print("");
                    _print.flush();
                    start();
                }
            }
        }
        else {
            telnetOutput(5, "Connection attempt from "+ client.getInetAddress().getHostAddress() +" rejected.");
            _cSocket.close();
        }
    }
    
    @SuppressWarnings("deprecation")
    public void run()
    {
        String _usrCommand = "";
        try
        {
            while (_usrCommand.compareTo("quit") != 0 && _usrCommand.compareTo("exit") != 0)
            {
                _usrCommand = _read.readLine();
                if(_usrCommand == null)
                {
                	_cSocket.close();
                	break;
                }
                if (_usrCommand.equals("help")) {
                    _print.println("The following is a list of all available commands: ");
                    _print.println("help                - shows this help.");
                    _print.println("status              - displays basic server statistics.");
                    _print.println("performance         - shows server performance statistics.");
                    _print.println("purge               - removes finished threads from thread pools.");
                    _print.println("announce <text>     - announces <text> in game.");
                    _print.println("msg <nick> <text>   - Sends a whisper to char <nick> with <text>.");
                    _print.println("gmchat <text>       - Sends a message to all GMs with <text>.");
                    _print.println("gmlist              - lists all gms online.");
                    _print.println("kick                - kick player <name> from server.");
                    _print.println("shutdown <time>     - shuts down server in <time> seconds.");
                    _print.println("restart <time>      - restarts down server in <time> seconds.");
                    _print.println("abort               - aborts shutdown/restart.");
                    _print.println("give <player> <itemid> <amount>");
                    _print.println("extreload <name>    - reload and initializes the named extension or all if used without argument");
                    _print.println("extinit <name>      - initilizes the named extension or all if used without argument");
                    _print.println("extunload <name>    - unload the named extension or all if used without argument");
                    _print.println("debug <cmd>         - executes the debug command (see 'help debug').");
                    _print.println("jail <player> [time]");
                    _print.println("unjail <player>");
                     _print.println("reload <...>");
                     _print.println("reload_config <file>");
                	if(Config.IRC_ENABLED)
                	{
                        _print.println("ircc <command>  	- sends a command to irc");
	                    _print.println("ircm <target ><msg> - sends a message to irc");
                	}
                    _print.println("quit                - closes telnet session.");
                }
                else if(_usrCommand.equals("help debug"))
                {
                	_print.println("The following is a list of all available debug commands: ");
                	_print.println("decay               - prints info about the DecayManager");
                	_print.println("PacketTP            - prints info about the General Packet ThreadPool");
                	_print.println("IOPacketTP          - prints info about the I/O Packet ThreadPool");
                	_print.println("GeneralTP           - prints info about the General ThreadPool");
                }
                else if (_usrCommand.equals("status"))
                {
                    int playerCount = 0, objectCount = 0;
                    int max = LoginServerThread.getInstance().getMaxPlayer();

                    playerCount = L2World.getInstance().getAllPlayersCount();
                    objectCount = L2World.getInstance().getAllVisibleObjectsCount();
                    
                    int itemCount=0;
                    int itemVoidCount=0;
                    int monsterCount=0;
                    int minionCount = 0;
                    int minionsGroupCount = 0;
                    int npcCount=0;
                    int charCount=0;
                    int pcCount=0;
                    int doorCount=0;
                    int summonCount=0;
                    int AICount=0;

		            for (L2Object obj : L2World.getInstance().getAllVisibleObjects())
		            {
		            	if(obj == null)
		            		continue;
						if (obj instanceof L2Character)
							if (((L2Character)obj).hasAI())
								AICount++;
                        if (obj instanceof L2ItemInstance)
                            if (((L2ItemInstance)obj).getLocation() == L2ItemInstance.ItemLocation.VOID)
                                itemVoidCount++;
                            else
                                itemCount++;
                        
                        else if (obj instanceof L2MonsterInstance)
                        {
                            monsterCount++;
                            minionCount += ((L2MonsterInstance)obj).getTotalSpawnedMinionsInstances();
                            minionsGroupCount += ((L2MonsterInstance)obj).getTotalSpawnedMinionsGroups();
                        }
                        else if (obj instanceof L2NpcInstance)
                            npcCount++;
                        else if (obj instanceof L2PcInstance)
                            pcCount++;
                        else if (obj instanceof L2Summon)
                            summonCount++;
                        else if (obj instanceof L2DoorInstance)
                            doorCount++;
                        else if (obj instanceof L2Character)
                            charCount++;
                    }
                    _print.println("Server Status: ");
                    _print.println("  --->  Player Count: " + playerCount + "/" + max); 
                    _print.println("  +-->  Object Count: " + objectCount);
                    _print.println("  +-->      AI Count: " + AICount);
                    _print.println("  +.... L2Item(Void): " + itemVoidCount);
                    _print.println("  +.......... L2Item: " + itemCount);
                    _print.println("  +....... L2Monster: " + monsterCount);
                    _print.println("  +......... Minions: " + minionCount);
                    _print.println("  +.. Minions Groups: " + minionsGroupCount);
                    _print.println("  +........... L2Npc: " + npcCount);
                    _print.println("  +............ L2Pc: " + pcCount);
                    _print.println("  +........ L2Summon: " + summonCount);
                    _print.println("  +.......... L2Door: " + doorCount);
                    _print.println("  +.......... L2Char: " + charCount);
                    _print.println("  --->   Ingame Time: " + gameTime());
                    _print.println("  ---> Server Uptime: " + getUptime(_uptime));
                    _print.println("  --->      GM Count: " + getOnlineGMS());
                    _print.println("  --->       Threads: " + Thread.activeCount());
                    _print.println("  RAM Used: "+((Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576)); // 1024 * 1024 = 1048576
                    _print.flush();
                }
                else if (_usrCommand.equals("performance"))
                {
                    for (String line : ThreadPoolManager.getInstance().getStats())
                    {
                        _print.println(line);
                    }
                    _print.flush();
                }
                else if (_usrCommand.equals("purge"))
                {
                	ThreadPoolManager.getInstance().purge();
                	_print.println("STATUS OF THREAD POOLS AFTER PURGE COMMAND:");
                	_print.println("");
                	for (String line : ThreadPoolManager.getInstance().getStats())
                	{
                		_print.println(line);
                	}
                	_print.flush();
                }
                else if (_usrCommand.startsWith("announce"))
                {
                    try
                    {
                        _usrCommand = _usrCommand.substring(9);
                        Announcements.getInstance().announceToAll(_usrCommand);
                        _print.println("Announcement Sent!");
                    }
                    catch (StringIndexOutOfBoundsException e)
                    {
                        _print.println("Please Enter Some Text To Announce!");
                    }
                }
                else if (_usrCommand.startsWith("msg"))
                {
                    try
                    {
                        String val = _usrCommand.substring(4);
                        StringTokenizer st = new StringTokenizer(val);
                        String name = st.nextToken();
                        String message = val.substring(name.length()+1);
                        L2PcInstance reciever = L2World.getInstance().getPlayer(name);
                        CreatureSay cs = new CreatureSay(0, Say2.TELL, "Telnet Priv", message);
                        if(reciever != null)
                        {
                            reciever.sendPacket(cs);
                            _print.println("Telnet Priv->" + name + ": " + message);
                            _print.println("Message Sent!");
                        }
                        else
                        {
                            _print.println("Unable To Find Username: " + name);
                        }
                    }
                    catch (StringIndexOutOfBoundsException e)
                    {
                        _print.println("Please Enter Some Text!");
                    }
                }
                else if (_usrCommand.startsWith("gmchat"))
                {
                    try
                    {
                        _usrCommand = _usrCommand.substring(7);
                        CreatureSay cs = new CreatureSay(0, 9, "Telnet GM Broadcast from " + _cSocket.getInetAddress().getHostAddress(), _usrCommand);
                        GmListTable.broadcastToGMs(cs);
                        _print.println("Your Message Has Been Sent To " + getOnlineGMS() + " GM(s).");
                    }
                    catch (StringIndexOutOfBoundsException e)
                    {
                        _print.println("Please Enter Some Text To Announce!");
                    }
                }
                else if (_usrCommand.equals("gmlist"))
                {
                    int igm = 0;
                    String gmList = "";
                    
                    for (String player : GmListTable.getInstance().getAllGmNames(true))
                    {
                            gmList = gmList + ", " + player;
                            igm++;
                    }
                    _print.println("There are currently " + igm +" GM(s) online...");
                    if ( gmList != "" ) _print.println(gmList);
                }
                /*else if (_usrCommand.startsWith("unblock"))
                {
                    try
                    {
                        _usrCommand = _usrCommand.substring(8);
                        if (LoginServer.getInstance().unblockIp(_usrCommand))
                        {
                            _log.warn("IP removed via TELNET by host: " + _cSocket.getInetAddress().getHostAddress());
                            _print.println("The IP " + _usrCommand + " has been removed from the hack protection list!");
                        }
                        else
                        {
                            _print.println("IP not found in hack protection list...");
                        }
                        //TODO: with packet
                    }
                    catch (StringIndexOutOfBoundsException e)
                    {
                        _print.println("Please Enter the IP to Unblock!");
                    }
                }*/
                else if (_usrCommand.startsWith("kick"))
                {
                    try
                    {
                        _usrCommand = _usrCommand.substring(5);
                        L2PcInstance player = L2World.getInstance().getPlayer(_usrCommand);
                        if(player != null)
                        {
                            player.sendMessage("You are kicked by gm");
                            try {
                                L2GameClient.saveCharToDisk(player);
                                player.sendPacket(new LeaveWorld());
                                player.deleteMe();
                                player.logout();
                                } catch (Throwable t)   {}
                            _print.println("Player kicked");
                        }
                    }
                    catch (StringIndexOutOfBoundsException e)
                    {
                        _print.println("Please enter player name to kick");
                    }
                }
                else if (_usrCommand.startsWith("shutdown"))
                {
                    try
                    {
                        int val = Integer.parseInt(_usrCommand.substring(9)); 
                        Shutdown.getInstance().startShutdown(_cSocket.getInetAddress().getHostAddress(), val,Shutdown.shutdownModeType.SHUTDOWN);
                        _print.println("Server Will Shutdown In " + val + " Seconds!");
                        _print.println("Type \"abort\" To Abort Shutdown!");
                    }
                    catch (StringIndexOutOfBoundsException e)
                    {
                        _print.println("Please Enter * amount of seconds to shutdown!");
                    }
                    catch (Exception NumberFormatException) {
                        _print.println("Numbers Only!");
                    }
                }
                else if (_usrCommand.startsWith("restart"))
                {
                    try
                    {
                        int val = Integer.parseInt(_usrCommand.substring(8)); 
                        Shutdown.getInstance().startShutdown(_cSocket.getInetAddress().getHostAddress(), val,Shutdown.shutdownModeType.RESTART);
                        _print.println("Server Will Restart In " + val + " Seconds!");
                        _print.println("Type \"abort\" To Abort Restart!");
                    }
                    catch (StringIndexOutOfBoundsException e)
                    {
                        _print.println("Please Enter * amount of seconds to restart!");
                    }
                    catch (Exception NumberFormatException) {
                        _print.println("Numbers Only!");
                    }
                }
                else if (_usrCommand.startsWith("abort"))
                {
                    Shutdown.getInstance().abort(_cSocket.getInetAddress().getHostAddress());
                    _print.println("OK! - Shutdown/Restart Aborted.");
                }
                else if (_usrCommand.equals("quit")) { /* Do Nothing :p - Just here to save us from the "Command Not Understood" Text */ }
                else if (_usrCommand.startsWith("give"))
                {
                    StringTokenizer st = new StringTokenizer(_usrCommand.substring(5));
                    
                    try
                    {
                        L2PcInstance player = L2World.getInstance().getPlayer(st.nextToken());
                        int itemId = Integer.parseInt(st.nextToken());
                        int amount = Integer.parseInt(st.nextToken());
                        
                        if(player != null)
                        {
                            L2ItemInstance item = player.getInventory().addItem("Status-Give", itemId, amount, null, null);
                            InventoryUpdate iu = new InventoryUpdate();
                            iu.addItem(item);
                            SystemMessage sm = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2);
                            sm.addItemName(itemId);
                            sm.addNumber(amount);
                            player.sendPacket(iu);
                            _print.println("ok");
                        }
                    }
                    catch(Exception e)
                    {
                        
                    }
                }
                else if (_usrCommand.startsWith("jail"))
                {
                	StringTokenizer st = new StringTokenizer(_usrCommand.substring(5));
                    try
                    {
                        L2PcInstance playerObj = L2World.getInstance().getPlayer(st.nextToken());
                        int delay = 0;
                        try
                        {
                            delay = Integer.parseInt(st.nextToken());
                        } catch (NumberFormatException nfe) {
                        } catch (NoSuchElementException nsee) {}
                        //L2PcInstance playerObj = L2World.getInstance().getPlayer(player);

                        if (playerObj != null)
                        {
                            playerObj.setInJail(true, delay);
                            _print.println("Character "+playerObj.getName()+" jailed for "+(delay>0 ? delay+" minutes." : "ever!"));
                        } else
                        	jailOfflinePlayer(playerObj.getName(), delay);
                    } catch (NoSuchElementException nsee) 
                    {
                    	_print.println("Specify a character name.");
                    } catch(Exception e)
                    {
                        if (_log.isDebugEnabled()) _log.error(e.getMessage(),e);
                    }
                }
                else if (_usrCommand.startsWith("unjail"))
                {
                	StringTokenizer st = new StringTokenizer(_usrCommand.substring(7));
                	try
                    {
                        L2PcInstance playerObj = L2World.getInstance().getPlayer(st.nextToken());

                        if (playerObj != null)
                        {
                            playerObj.stopJailTask(false);
                            playerObj.setInJail(false, 0);
                            _print.println("Character "+playerObj.getName()+" removed from jail");
                        } else
                        	unjailOfflinePlayer(playerObj.getName());
                    } catch (NoSuchElementException nsee) 
                    {
                    	_print.println("Specify a character name.");
                    } catch(Exception e)
                    {
                        if (_log.isDebugEnabled()) _log.debug(e.getMessage(),e);;
                    }
                }
                else if (_usrCommand.startsWith("ircc"))
                {
                	if(Config.IRC_ENABLED)
                	{
	                	_usrCommand = _usrCommand.substring(4);
	                	try
	                    {
	                		IrcManager.getInstance().getConnection().send(_usrCommand);
	                		
	                    } catch(Exception e)
	                    {
	                        if (_log.isDebugEnabled()) _log.debug(e.getMessage(),e);;
	                    }
                	}
                }
                else if (_usrCommand.startsWith("ircm"))
                {
                	if(Config.IRC_ENABLED)
                	{
	                    String val = _usrCommand.substring(4);
	                	try
	                	{
		                    StringTokenizer st = new StringTokenizer(val);
		                    String name = st.nextToken();
		                    String message = val.substring(name.length()+1);
	                		IrcManager.getInstance().getConnection().send(name,message);
	                		
	                    } catch(Exception e)
	                    {
	                        if (_log.isDebugEnabled()) _log.debug(e.getMessage(),e);;
	                    }
                	}
                }
                else if (_usrCommand.startsWith("debug") && _usrCommand.length() > 6)
                {
                	StringTokenizer st = new StringTokenizer(_usrCommand.substring(6));
                	try
                	{
                		String dbg = st.nextToken();
                		
                		if(dbg.equals("decay"))
                		{
                			_print.print(DecayTaskManager.getInstance().toString());
                		}
                		else if(dbg.equals("ai"))
                		{
                			/*
                			_print.println("AITaskManagerStats");
                			for(String line : AITaskManager.getInstance().getStats())
                			{
                				_print.println(line);
                			}
                			*/
                		}
                		else if(dbg.equals("aiflush"))
                		{
                			//AITaskManager.getInstance().flush();
                		}
                		else if(dbg.equals("PacketTP"))
                		{
                			String str = ThreadPoolManager.getInstance().getPacketStats();
                			_print.println(str);
                			int i = 0;
                			File f = new File("./log/StackTrace-PacketTP-"+i+".txt");
                			while(f.exists())
                			{
                				i++;
                				f = new File("./log/StackTrace-PacketTP-"+i+".txt");
                			}
                			f.getParentFile().mkdirs();
                			FileOutputStream fos = new FileOutputStream(f);
                			OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
                			out.write(str);
                			out.flush();
                			out.close();
                			fos.close();
                		}
                		else if(dbg.equals("IOPacketTP"))
                		{
                			String str = ThreadPoolManager.getInstance().getIOPacketStats();
                			_print.println(str);
                			int i = 0;
                			File f = new File("./log/StackTrace-IOPacketTP-"+i+".txt");
                			while(f.exists())
                			{
                				i++;
                				f = new File("./log/StackTrace-IOPacketTP-"+i+".txt");
                			}
                			f.getParentFile().mkdirs();
                			FileOutputStream fos = new FileOutputStream(f);
                			OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
                			out.write(str);
                			out.flush();
                			out.close();
                			fos.close();
                		}
                		else if(dbg.equals("GeneralTP"))
                		{
                			String str = ThreadPoolManager.getInstance().getGeneralStats();
                			_print.println(str);
                			int i = 0;
                			File f = new File("./log/StackTrace-GeneralTP-"+i+".txt");
                			while(f.exists())
                			{
                				i++;
                				f = new File("./log/StackTrace-GeneralTP-"+i+".txt");
                			}
                			f.getParentFile().mkdirs();
                			FileOutputStream fos = new FileOutputStream(f);
                			OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
                			out.write(str);
                			out.flush();
                			out.close();
                			fos.close();
                		}
                	}
                	catch(Exception e){}
                }
                else if(_usrCommand.startsWith("reload_config"))
                {
                    StringTokenizer st = new StringTokenizer(_usrCommand);
                    st.nextToken();

                    try
                    {
                        String type = st.nextToken();
                        if (type.equals("all"))
                        {
                            Config.load();
                            _print.println("All configs reloaded");
                        }
                        else if(type.equals("rates"))
                        {
                            Config.loadRatesConfig();
                            _print.println("Rates config reloaded");
                        }
                        else if(type.equals("enchant"))
                        {
                            Config.loadEnchantConfig();
                            _print.println("Enchant config reloaded");
                        }
                        else if(type.equals("pvp"))
                        {
                            Config.loadPvpConfig();
                            _print.println("Pvp config reloaded");
                        }
                        else if(type.equals("options"))
                        {
                            Config.loadOptionsConfig();
                            _print.println("Options config reloaded");
                        }
                        else if(type.equals("other"))
                        {
                            Config.loadOtherConfig();
                            _print.println("Other config reloaded");
                        }
                        else if(type.equals("alt"))
                        {
                            Config.loadAltConfig();
                            _print.println("Alt config reloaded");
                        }
                        else if(type.equals("clans"))
                        {
                            Config.loadClansConfig();
                            _print.println("Clans config reloaded");
                        }
                        else if(type.equals("champions"))
                        {
                            Config.loadChampionsConfig();
                            _print.println("Champions config reloaded");
                        }
                        else if(type.equals("lottery"))
                        {
                            Config.loadLotteryConfig();
                            _print.println("Lottery config reloaded");
                        }
                        else if(type.equals("sepulchurs"))
                        {
                            Config.loadSepulchursConfig();
                            _print.println("Sepulchurs config reloaded");
                        }
                        else if(type.equals("clanhall"))
                        {
                            Config.loadClanHallConfig();
                            _print.println("Clanhall config reloaded");
                        }
                        else if(type.equals("funengines"))
                        {
                            Config.loadFunEnginesConfig();
                            _print.println("Fun egines config reloaded");
                        }
                        else if(type.equals("sevensigns"))
                        {
                            Config.loadSevenSignsConfig();
                            _print.println("Seven Signs config reloaded");
                        }
                        else if(type.equals("gmconf"))
                        {
                            Config.loadGmAccess();
                            _print.println("Gm config reloaded");
                        }
                        else if(type.equals("irc"))
                        {
                            Config.loadIrcConfig();
                            _print.println("Irc config reloaded");
                        }
                        else if(type.equals("boss"))
                        {
                            Config.loadBossConfig();
                            _print.println("Boss config reloaded");
                        }
                        else if(type.equals("sayfilter"))
                        {
                            Config.loadSayFilter();
                            _print.println("Sayfilter reloaded");
                        }
                        else if(type.equals("access"))
                        {
                            Config.loadPrivilegesConfig();
                            _print.println("Access config reloaded");
                        }
                        else if(type.equals("siege"))
                        {
                            SiegeManager.getInstance().reload();
                            _print.println("Siege config reloaded");
                        }
                        else if(type.equals("wedding"))
                        {
                            Config.loadWeddingConfig();
                            _print.println("Wedding config reloaded");
                        }
                        else
                        {
                            _print.println("Usage:  reload_config <all|rates|enchant|pvp|options|other|alt|olympiad|clans|champions|lottery|sepulchurs|clanhall|funengines|sevensigns|gmconf|access|irc|boss|sayfilter|siege|wedding>");
                        }
                    }
                    catch(Exception e)
                    {
                        _print.println("Usage:  reload_config <all|rates|enchant|pvp|options|other|alt|olympiad|clans|champions|lottery|sepulchurs|clanhall|funengines|sevensigns|gmconf|access|irc|boss|sayfilter|siege|wedding>");
                    }
                }
                else if (_usrCommand.startsWith("reload"))
                {
                	StringTokenizer st = new StringTokenizer(_usrCommand);
                   st.nextToken();
                	try
                	{
                		String type = st.nextToken();
                		
                		if(type.equals("multisell"))
                		{
                			_print.print("Reloading multisell... ");
                			L2Multisell.getInstance().reload();
                			_print.print("done\n");
                		}
						else if(type.equals("teleport"))
                        {
                            _print.print("Reloading teleports... ");
                            TeleportLocationTable.getInstance().reloadAll();
                            _print.print("done\n");
                        }
                		else if(type.equals("skill"))
                		{
                			_print.print("Reloading skills... ");
                			SkillTable.getInstance().reload();
                			_print.print("done\n");
                		}
                		else if(type.equals("npc"))
                		{
                			_print.print("Reloading npc templates... ");
                			NpcTable.getInstance().cleanUp();
                			NpcTable.getInstance().reloadAll();
                			_print.print("done\n");
                		}
                		else if(type.equals("htm"))
                		{
                			_print.print("Reloading html cache... ");
                			HtmCache.getInstance().reload();
                			_print.print("done\n");
                		}
                		else if(type.equals("item"))
                		{
                			_print.print("Reloading item templates... ");
                			ItemTable.getInstance().reload();
                			_print.print("done\n");
                		}
                		else if(type.equals("instancemanager"))
                		{
                			_print.print("Reloading instance managers... ");
                			Manager.reloadAll();
                			_print.print("done\n");
                		}
                		else if(type.equals("zone"))
                		{
                			_print.print("Reloading zone tables... ");
                			ZoneManager.getInstance().reload();
                            _print.print("done\n");
                	    }
						else if(type.equals("tradelist"))
						{
							_print.print("Reloading trade lists...");
							TradeListTable.getInstance().reloadAll();
							_print.print("done\n");
						}
                        else
                        {
                           _print.println("Usage: reload <multisell|teleport|skill|npc|htm|item|instancemanager|tradelist|zone>");
                        }
                	}
                    catch(Exception e)
                    {
                        _print.println("Usage: reload <multisell|teleport|skill|npc|htm|item|instancemanager|tradelist|zone>");
                    }
                }
                else if (_usrCommand.startsWith("gamestat"))
                {
                	StringTokenizer st = new StringTokenizer(_usrCommand.substring(9));
                	try
                	{
                		String type = st.nextToken();
                		
                		// name;type;x;y;itemId:enchant:price...
                		if(type.equals("privatestore"))
                		{
                			for(L2PcInstance player : L2World.getInstance().getAllPlayers())
                			{
                				if(player.getPrivateStoreType() == 0)
                					continue;
                				
                				TradeList list = null;
                				String content = "";

                				if(player.getPrivateStoreType() == 1) // sell
                				{
                					list = player.getSellList();                					
                					for(TradeItem item : list.getItems())
                					{
                						content += item.getItem().getItemId()+":"+item.getEnchant()+":"+item.getPrice()+":";
                					}
                					content = player.getName()+";"+"sell;"+player.getX()+";"+player.getY()+";"+content;
                					_print.println(content);
                					continue;
                				}
                				else if(player.getPrivateStoreType() == 3) // buy
                				{
                					list = player.getBuyList();                					
                					for(TradeItem item : list.getItems())
                					{
                						content += item.getItem().getItemId()+":"+item.getEnchant()+":"+item.getPrice()+":";
                					}
                					content = player.getName()+";"+"buy;"+player.getX()+";"+player.getY()+";"+content;
                					_print.println(content);
                					continue;
                				}

                			}
                		}
                	}
                	catch(Exception e){}
                }
                else if (_usrCommand.startsWith("extreload")) {
                    String[] args = _usrCommand.split("\\s+");
                    if (args.length > 1) {
                        for (int i = 1; i < args.length; i++)
                            DynamicExtension.getInstance().reload(args[i]);
                    } else {
                        DynamicExtension.getInstance().reload();
                    }
                }
                else if (_usrCommand.startsWith("extinit")) {
                    String[] args = _usrCommand.split("\\s+");
                    if (args.length > 1) {
                        for (int i = 1; i < args.length; i++)
                            DynamicExtension.getInstance().initExtension(args[i]);
                    } else {
                        DynamicExtension.getInstance().initExtensions();
                    }
                }
                else if (_usrCommand.startsWith("extunload")) {
                    String[] args = _usrCommand.split("\\s+");
                    if (args.length > 1) {
                        for (int i = 1; i < args.length; i++)
                            DynamicExtension.getInstance().unloadExtension(args[i]);
                    } else {
                        DynamicExtension.getInstance().unloadExtensions();
                    }
                }
                else if (_usrCommand.startsWith("get")) {
                    Object o = null;
                    try {
                        String[] args = _usrCommand.substring(3).split("\\s+");
                        if (args.length == 1)
                            o = DynamicExtension.getInstance().get(args[0], null);
                        else
                            o = DynamicExtension.getInstance().get(args[0], args[1]);
                    } catch (Exception ex) {
                        _print.print(ex.toString() + "\r\n");
                    }
                    if (o != null)
                        _print.print(o.toString() + "\r\n");
                }
                else if (_usrCommand.length() > 0) {
                    try {
                        String[] args = _usrCommand.split("\\s+");
                        if (args.length == 1)
                            DynamicExtension.getInstance().set(args[0], null, null);
                        else if (args.length == 2)
                            DynamicExtension.getInstance().set(args[0], null, args[1]);
                        else
                            DynamicExtension.getInstance().set(args[0], args[1], args[2]);
                    } catch (Exception ex) {
                        _print.print(ex.toString());
                    }
                }
                else if (_usrCommand.length() == 0) { /* Do Nothing Again - Same reason as the quit part */ }
                _print.print("");
                _print.flush();
            }
            if(!_cSocket.isClosed())
            {
	            _print.println("Bye Bye!");
	            _print.flush();
	            _cSocket.close();
            }
            telnetOutput(1, "Connection from "+_cSocket.getInetAddress().getHostAddress()+" was closed by client.");
        }
        catch (IOException e)
        {
            _log.error(e.getMessage(),e);
        }
    }
    
    private void jailOfflinePlayer(String name, int delay)
    {
    	Connection con = null;
      	try
       	{
       		con = L2DatabaseFactory.getInstance().getConnection(con);
       		
       		PreparedStatement statement = con.prepareStatement("UPDATE characters SET x=?, y=?, z=?, in_jail=?, jail_timer=? WHERE char_name=?");
       		statement.setInt(1, -114356);
       		statement.setInt(2, -249645);
       		statement.setInt(3, -2984);
       		statement.setInt(4, 1);
       		statement.setLong(5, delay * 60000L);
       		statement.setString(6, name);
    
    		statement.execute();
    		int count = statement.getUpdateCount();
    		statement.close();
    
    		if (count == 0)
    			_print.println("Character not found!");
    		else
    			_print.println("Character "+name+" jailed for "+(delay>0 ? delay+" minutes." : "ever!"));
       	} catch (SQLException se)
       	{
       		_print.println("SQLException while jailing player");
            if (_log.isDebugEnabled()) se.printStackTrace();
       	} finally
       	{
       		try { con.close(); } catch (Exception e) {}
       	}
    }
    
    private void unjailOfflinePlayer(String name)
    {
    	Connection con = null;
      	try
       	{
       		con = L2DatabaseFactory.getInstance().getConnection(con);
       		
       		PreparedStatement statement = con.prepareStatement("UPDATE characters SET x=?, y=?, z=?, in_jail=?, jail_timer=? WHERE char_name=?");
       		statement.setInt(1, 17836);
       		statement.setInt(2, 170178);
       		statement.setInt(3, -3507);
       		statement.setInt(4, 0);
       		statement.setLong(5, 0);
       		statement.setString(6, name);
    
    		statement.execute();
    		int count = statement.getUpdateCount();
    		statement.close();
    
    		if (count == 0)
    			_print.println("Character not found!");
    		else
    			_print.println("Character "+name+" set free.");
       	} catch (SQLException se)
       	{
       		_print.println("SQLException while jailing player");
            if (_log.isDebugEnabled()) se.printStackTrace();
       	} finally
       	{
       		try { con.close(); } catch (Exception e) {}
       	}
    }
    
    private int getOnlineGMS()
    {
        return GmListTable.getInstance().getAllGms(true).length;
    }
    
    private String getUptime(int time)
    {
        int uptime = (int)System.currentTimeMillis() - time;
        uptime = uptime / 1000;
        int h = uptime / 3600;
        int m = (uptime-(h*3600))/60;
        int s = ((uptime-(h*3600))-(m*60));
        return h + "hrs " + m + "mins " + s + "secs";
    }
    
    private String gameTime()
    {
        int t = GameTimeController.getInstance().getGameTime();
        int h = t/60;
        int m = t%60;
        SimpleDateFormat format = new SimpleDateFormat("H:mm");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        return format.format(cal.getTime());
    }
}
