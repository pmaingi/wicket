/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.protocol.http;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.RequestCycle;
import org.apache.wicket.util.string.Strings;
import org.apache.wicket.util.value.ValueMap;

/**
 * Wicket Http specific utilities class.
 */
public final class RequestUtils
{
	/**
	 * Decode the provided queryString as a series of key/ value pairs and set them in the provided
	 * value map.
	 * 
	 * @param queryString
	 *            string to decode, uses '&' to separate parameters and '=' to separate key from
	 *            value
	 * @param params
	 *            parameters map to write the found key/ value pairs to
	 */
	public static void decodeParameters(String queryString, ValueMap params)
	{
		final String[] paramTuples = queryString.split("&");
		for (int t = 0; t < paramTuples.length; t++)
		{
			final String[] bits = paramTuples[t].split("=");
			if (bits.length == 2)
			{
				params.add(WicketURLDecoder.QUERY_INSTANCE.decode(bits[0]),
					WicketURLDecoder.QUERY_INSTANCE.decode(bits[1]));
			}
			else
			{
				params.add(WicketURLDecoder.QUERY_INSTANCE.decode(bits[0]), "");
			}
		}
	}

	public static void decodeUrlParameters(String queryString, Map parameters)
	{
		Map temp = new HashMap();
		final String[] paramTuples = queryString.split("&");
		for (int t = 0; t < paramTuples.length; t++)
		{
			final String[] bits = paramTuples[t].split("=");
			final String key;
			final String value;
			key = WicketURLDecoder.QUERY_INSTANCE.decode(bits[0]);
			if (bits.length == 2)
			{
				value = WicketURLDecoder.QUERY_INSTANCE.decode(bits[1]);
			}
			else
			{
				value = "";
			}
			List l = (List)temp.get(key);
			if (l == null)
			{
				l = new ArrayList();
				temp.put(key, l);
			}
			l.add(value);
		}

		Iterator entries = temp.entrySet().iterator();
		while (entries.hasNext())
		{
			Map.Entry entry = (Entry)entries.next();
			List lst = (List)entry.getValue();
			String s[] = new String[lst.size()];
			lst.toArray(s);
			parameters.put(entry.getKey(), s);
		}
	}

	/**
	 * Remove occurrences of ".." from the path
	 * 
	 * @param path
	 * @return
	 */
	static String removeDoubleDots(String path)
	{
		List newcomponents = new ArrayList(Arrays.asList(path.split("/")));

		for (int i = 0; i < newcomponents.size(); i++)
		{
			if (i < newcomponents.size() - 1)
			{
				// Verify for a ".." component at next iteration
				if (((String)newcomponents.get(i)).length() > 0 &&
					newcomponents.get(i + 1).equals(".."))
				{
					newcomponents.remove(i);
					newcomponents.remove(i);
					i = i - 2;
					if (i < -1)
						i = -1;
				}
			}
		}
		String newpath = Strings.join("/", (String[])newcomponents.toArray(new String[0]));
		if (path.endsWith("/"))
			return newpath + "/";
		return newpath;
	}

	/**
	 * Hidden utility class constructor.
	 */
	private RequestUtils()
	{
	}

	/**
	 * @deprecated Use {@link WicketURLDecoder} instead.
	 */
	public static String decode(String path)
	{
		return WicketURLDecoder.QUERY_INSTANCE.decode(path);
	}

	/**
	 * @deprecated Use {@link WicketURLEncoder} instead.
	 */
	public static String encode(String value)
	{
		return WicketURLEncoder.QUERY_INSTANCE.encode(value);
	}

	/**
	 * Calculates absolute path to url relative to another absolute url.
	 * 
	 * @param relativePagePath
	 *            path, relative to requestPath
	 * @return absolute path for given url
	 */
	public final static String toAbsolutePath(final String relativePagePath)
	{
		HttpServletRequest req = ((WebRequest)RequestCycle.get().getRequest()).getHttpServletRequest();
		return toAbsolutePath(req.getRequestURL().toString(), relativePagePath);
	}

	/**
	 * Calculates absolute path to url relative to another absolute url.
	 * 
	 * @param requestPath
	 *            absolute path.
	 * @param relativePagePath
	 *            path, relative to requestPath
	 * @return absolute path for given url
	 */
	public final static String toAbsolutePath(final String requestPath,
		final String relativePagePath)
	{
		final StringBuffer result;
		if (requestPath.endsWith("/"))
		{
			result = new StringBuffer(requestPath);
		}
		else
		{
			// Remove everything after last slash (but not slash itself)
			result = new StringBuffer(requestPath.substring(0, requestPath.lastIndexOf('/') + 1));
		}

		if (relativePagePath.startsWith("../"))
		{
			StringBuffer tempRelative = new StringBuffer(relativePagePath);

			// Go up through hierarchy until we find most common directory for both pathes.
			while (tempRelative.indexOf("../") == 0)
			{
				// Delete ../ from relative path
				tempRelative.delete(0, 3);

				// Delete last slash from result
				result.setLength(result.length() - 1);

				// Delete everyting up to last slash
				result.delete(result.lastIndexOf("/") + 1, result.length());
			}
			result.append(tempRelative);
		}
		else
		{
			// Pages are in the same directory
			result.append(relativePagePath);
		}
		return result.toString();
	}
}