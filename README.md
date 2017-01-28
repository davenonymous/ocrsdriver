# ocrsdriver
OpenComputers + Refined Storage = :heart:

Allows OpenComputers to access Refined Storage grid nodes via Adapters.
Just a toy project :airplane: for my own usage.

## Known Issues

- This is currently unrestricted, i.e. you can export items using just a cable - and fast.
  I'll probably restricted this in the future to Interfaces and Export Buses, but for now it is what it is.
- You can not request crafting jobs at the moment.
- No Fluid export at the moment.

## Available Commands

| Method                               | Description                                                      |
| ------------------------------------ | ---------------------------------------------------------------- |
| isConnected()                        | Returns whether the grid node is connected to a network          |
| getEnergyUsage()                     | Returns the energy usage of the whole network in RS/tick         |
| getCraftingTasks()                   | Returns a list of crafting tasks the system has currently queued |
| getPatterns()                        | Returns a list of all crafting patterns in the network           |
| hasPattern(itemstack)                | Returns true if the system has a pattern for the given itemstack |
| getItems()                           | Returns a list of all itemstacks stored in the network           |
| getFluids()                          | Returns a list of all fluid stacks stored in the network         |
| extractItem(itemstack, amount, side) | Extracts the given amount of itemstack to the specified side and returns the number of transfered items. |

## Examples

### Drop all items you got more than 8192 of

Place a Trash Can below an interface connected to an OpenComputers Adapter.
```lua
local component = require("component")
local sides = require("sides")

local rs = component.block_refinedstorage_interface

local limit = 8192
local side  = sides.down

for i,stack in ipairs(rs.getItems()) do
	while(stack.size > limit) do
		local dropped = rs.extractItem(stack, stack.size - limit, side)
		stack.size = stack.size - dropped
		if(dropped < 1) then
			break
		end
	end
end
```
