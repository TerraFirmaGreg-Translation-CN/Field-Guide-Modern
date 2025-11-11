package io.github.tfgcn.fieldguide.gsonadapter;

import com.google.gson.*;
import io.github.tfgcn.fieldguide.JsonUtils;
import io.github.tfgcn.fieldguide.patchouli.BookPage;
import io.github.tfgcn.fieldguide.patchouli.page.*;
import io.github.tfgcn.fieldguide.patchouli.page.tfc.*;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class LexiconPageAdapter implements JsonDeserializer<BookPage> {

	public final Map<String, Class<? extends BookPage>> pageTypes;

	public LexiconPageAdapter() {
		this.pageTypes = new HashMap<>();
		pageTypes.put("patchouli:text", PageText.class);
		pageTypes.put("patchouli:image", PageImage.class);
		pageTypes.put("patchouli:crafting", PageCrafting.class);
		pageTypes.put("patchouli:smelting", PageSmelting.class);
		pageTypes.put("patchouli:blasting", PageBlasting.class);
		pageTypes.put("patchouli:smoking", PageSmoking.class);
		pageTypes.put("patchouli:campfire", PageCampfireCooking.class);
		pageTypes.put("patchouli:smithing", PageSmithing.class);
		pageTypes.put("patchouli:stonecutting", PageStonecutting.class);
		pageTypes.put("patchouli:spotlight", PageSpotlight.class);
		pageTypes.put("patchouli:empty", PageEmpty.class);
		pageTypes.put("patchouli:entity", PageEntity.class);
		pageTypes.put("patchouli:multiblock", PageMultiblock.class);
		pageTypes.put("patchouli:link", PageLink.class);
		pageTypes.put("patchouli:relations", PageRelations.class);
		pageTypes.put("patchouli:quest", PageQuest.class);

		// TFC
		pageTypes.put("tfc:instant_barrel_recipe", PageBarrel.class);
		pageTypes.put("tfc:sealed_barrel_recipe", PageBarrel.class);
		pageTypes.put("tfc:heat_recipe", PageHeating.class);
		pageTypes.put("tfc:quern_recipe", PageQuern.class);
		pageTypes.put("tfc:loom_recipe", PageLoom.class);
		pageTypes.put("tfc:anvil_recipe", PageAnvil.class);
		pageTypes.put("tfc:welding_recipe", PageWelding.class);
		pageTypes.put("tfc:drying_recipe", PageDrying.class);
		pageTypes.put("tfc:glassworking_recipe", PageGlassworking.class);
		pageTypes.put("tfc:rock_knapping_recipe", PageRockKnapping.class);
		pageTypes.put("tfc:knapping_recipe", PageKnapping.class);
		pageTypes.put("tfc:multimultiblock", PageMultiMultiblock.class);
		pageTypes.put("tfc:table", PageTable.class);
		pageTypes.put("tfc:table_small", PageTable.class);
	}

	@Override
	public BookPage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
		// As a shortcut, an element of the pages array that is a string (instead of an object like normal)
		// will implicitly become a text page, with the text set to the content of the page.
		if (json instanceof JsonPrimitive prim && prim.isString()) {
			PageText out = new PageText();
			out.setText(prim.getAsString());
			return out;
		}

		JsonObject obj = json.getAsJsonObject();
		String type = obj.get("type").getAsString();
		boolean flag = false;
		if (type.indexOf(':') < 0) {
			type = "patchouli:" + type;
			flag = true;
		}
		Class<? extends BookPage> clazz = pageTypes.get(type);
		if (clazz == null) {
			clazz = PageTemplate.class;
		}
		BookPage page = JsonUtils.RAW_GSON.fromJson(json, clazz);
		if (flag) {
			page.setType(type);// Add default domain to the type
		}

		page.setJsonObject(obj);
		return page;
	}

}