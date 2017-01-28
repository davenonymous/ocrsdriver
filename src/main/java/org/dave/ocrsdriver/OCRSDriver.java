package org.dave.ocrsdriver;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = OCRSDriver.MODID, version = OCRSDriver.VERSION, dependencies = "required-after:OpenComputers;required-after:refinedstorage")
public class OCRSDriver {
    public static final String MODID = "ocrsdriver";
    public static final String VERSION = "1.0";

    @Mod.Instance
    public static OCRSDriver instance;
    
    @EventHandler
    public void init(FMLInitializationEvent event) {
        li.cil.oc.api.Driver.add(new DriverNetworkNode());
        li.cil.oc.api.Driver.add(new ConverterCraftingPattern());
        li.cil.oc.api.Driver.add(new ConverterCraftingTask());
    }
}
