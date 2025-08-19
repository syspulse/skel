package io.syspulse.skel.coingecko

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.concurrent.{ExecutionContext,Future,Await}

import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import spray.json._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util
import io.syspulse.skel.uri.CoingeckoURI
import io.syspulse.skel.blockchain.{Token,Coin,TokenProviderCoinGecko,Blockchain}

case class Coingecko_Coin(
  id:String,
  symbol:String,
  name:String
)

case class Coingecko_Image(
  thumb: String,
  small: String,
  large: String
)

case class Coingecko_Platform(
  decimal_place: Option[Int],
  contract_address: String
)


case class Coingecko_Links(
  homepage: Seq[String],
  whitepaper: Option[String],
  blockchain_site: Seq[String],
  official_forum_url: Seq[String],
  chat_url: Seq[String],
  announcement_url: Seq[String],
  snapshot_url: Option[String],
  twitter_screen_name: Option[String],
  facebook_username: Option[String],
  bitcointalk_thread_identifier: Option[Int],
  telegram_channel_identifier: Option[String],
  subreddit_url: Option[String],
  repos_url: Map[String, Seq[String]]
)

case class Coingecko_Description(
  en: String,
  de: Option[String]
)

case class Coingecko_Localization(
  en: String,
  de: Option[String]
)

case class Coingecko_Roi(
  times: Double,
  currency: String,
  percentage: Double
)

case class Coingecko_MarketData(
  current_price: Map[String, Double],
  total_value_locked: Option[Double],
  mcap_to_tvl_ratio: Option[Double],
  fdv_to_tvl_ratio: Option[Double],
  roi: Option[Coingecko_Roi],
  ath: Map[String, Double],
  ath_change_percentage: Map[String, Double],
  ath_date: Map[String, String],
  atl: Map[String, Double],
  atl_change_percentage: Map[String, Double],
  atl_date: Map[String, String],
  market_cap: Map[String, Double],
  market_cap_rank: Int,
  fully_diluted_valuation: Map[String, Double],
  market_cap_fdv_ratio: Option[Double],
  total_volume: Map[String, Double],
  high_24h: Map[String, Double],
  low_24h: Map[String, Double],
  price_change_24h: Double,
  price_change_percentage_24h: Double,
  price_change_percentage_7d: Double,
  price_change_percentage_14d: Double,
  price_change_percentage_30d: Double,
  price_change_percentage_60d: Double,
  price_change_percentage_200d: Double,
  price_change_percentage_1y: Double,
  market_cap_change_24h: Double,
  market_cap_change_percentage_24h: Double,
  price_change_24h_in_currency: Map[String, Double],
  price_change_percentage_1h_in_currency: Map[String, Double],
  price_change_percentage_24h_in_currency: Map[String, Double],
  price_change_percentage_7d_in_currency: Map[String, Double],
  price_change_percentage_14d_in_currency: Map[String, Double],
  price_change_percentage_30d_in_currency: Map[String, Double],
  price_change_percentage_60d_in_currency: Map[String, Double],
  price_change_percentage_200d_in_currency: Map[String, Double],
  price_change_percentage_1y_in_currency: Map[String, Double],
  market_cap_change_24h_in_currency: Map[String, Double],
  market_cap_change_percentage_24h_in_currency: Map[String, Double],
  total_supply: Option[Double],
  max_supply: Option[Double],
  max_supply_infinite: Option[Boolean],
  circulating_supply: Option[Double],
  last_updated: String
)

case class Coingecko_CommunityData(
  facebook_likes: Option[Int],
  reddit_average_posts_48h: Int,
  reddit_average_comments_48h: Int,
  reddit_subscribers: Int,
  reddit_accounts_active_48h: Int,
  telegram_channel_user_count: Option[Int]
)

case class Coingecko_DeveloperData(
  forks: Int,
  stars: Int,
  subscribers: Int,
  total_issues: Int,
  closed_issues: Int,
  pull_requests_merged: Int,
  pull_request_contributors: Int,
  code_additions_deletions_4_weeks: Option[Map[String, Int]],
  commit_count_4_weeks: Int,
  last_4_weeks_commit_activity_series: Seq[Int]
)

case class Coingecko_Market(
  name: String,
  identifier: String,
  has_trading_incentive: Boolean
)

case class Coingecko_Ticker(
  base: String,
  target: String,
  market: Coingecko_Market,
  last: Double,
  volume: Double,
  converted_last: Map[String, Double],
  converted_volume: Map[String, Double],
  trust_score: String,
  bid_ask_spread_percentage: Option[Double],
  timestamp: String,
  last_traded_at: String,
  last_fetch_at: String,
  is_anomaly: Boolean,
  is_stale: Boolean,
  trade_url: Option[String],
  token_info_url: Option[String],
  coin_id: String,
  target_coin_id: Option[String]
)

case class Coingecko_CoinData(
  id: String,
  symbol: String,
  name: String,
  web_slug: Option[String],
  asset_platform_id: Option[String],
  platforms: Option[Map[String, String]],
  detail_platforms: Map[String, Coingecko_Platform],
  block_time_in_minutes: Option[Int],
  hashing_algorithm: Option[String],
  categories: Seq[String],
  preview_listing: Boolean,
  public_notice: Option[String],
  additional_notices: Seq[String],
  localization: Coingecko_Localization,
  description: Coingecko_Description,
  links: Coingecko_Links,
  image: Coingecko_Image,
  country_origin: String,
  genesis_date: Option[String],
  sentiment_votes_up_percentage: Option[Double],
  sentiment_votes_down_percentage: Option[Double],
  watchlist_portfolio_users: Int,
  market_cap_rank: Int,
  market_data: Coingecko_MarketData,
  community_data: Coingecko_CommunityData,
  developer_data: Coingecko_DeveloperData,
  status_updates: Seq[String],
  last_updated: String,
  tickers: Seq[Coingecko_Ticker]
)

/*
{
  "id": "bitcoin",
  "symbol": "btc",
  "name": "Bitcoin",
  "web_slug": "bitcoin",
  "asset_platform_id": null,
  "platforms": {
    "": ""
  },
  "detail_platforms": {
    "": {
      "decimal_place": null,
      "contract_address": ""
    }
  },
  "block_time_in_minutes": 10,
  "hashing_algorithm": "SHA-256",
  "categories": [
    "FTX Holdings",
    "Cryptocurrency",
    "Proof of Work (PoW)",
    "Layer 1 (L1)"
  ],
  "preview_listing": false,
  "public_notice": null,
  "additional_notices": [],
  "localization": {
    "en": "Bitcoin",
    "de": "Bitcoin"
  },
  "description": {
    "en": "Bitcoin is the first successful internet money based on peer-to-peer technology...</a>.",
    "de": ""
  },
  "links": {
    "homepage": [
      "http://www.bitcoin.org",
      "",
      ""
    ],
    "whitepaper": "https://bitcoin.org/bitcoin.pdf",
    "blockchain_site": [
      "https://mempool.space/",
      "https://blockchair.com/bitcoin/",
      "https://btc.com/",
      "https://btc.tokenview.io/",
      "https://www.oklink.com/btc",
      "https://3xpl.com/bitcoin"
    ],
    "official_forum_url": [
      "https://bitcointalk.org/"
    ],
    "chat_url": [
      ""
    ],
    "announcement_url": [
      "",
      ""
    ],
    "snapshot_url": null,
    "twitter_screen_name": "bitcoin",
    "facebook_username": "bitcoins",
    "bitcointalk_thread_identifier": null,
    "telegram_channel_identifier": "",
    "subreddit_url": "https://www.reddit.com/r/Bitcoin/",
    "repos_url": {
      "github": [
        "https://github.com/bitcoin/bitcoin",
        "https://github.com/bitcoin/bips"
      ],
      "bitbucket": []
    }
  },
  "image": {
    "thumb": "https://assets.coingecko.com/coins/images/1/thumb/bitcoin.png?1696501400",
    "small": "https://assets.coingecko.com/coins/images/1/small/bitcoin.png?1696501400",
    "large": "https://assets.coingecko.com/coins/images/1/large/bitcoin.png?1696501400"
  },
  "country_origin": "",
  "genesis_date": "2009-01-03",
  "sentiment_votes_up_percentage": 84.07,
  "sentiment_votes_down_percentage": 15.93,
  "watchlist_portfolio_users": 1541900,
  "market_cap_rank": 1,
  "market_data": {
    "current_price": {
      "eth": 20.442233,
      "eur": 64375,
      "usd": 69840,
      "vef": 6993.03,  
      "sats": 100005210
    },
    "total_value_locked": null,
    "mcap_to_tvl_ratio": null,
    "fdv_to_tvl_ratio": null,
    "roi": null,
    "ath": {
      "eth": 624.203,
      "eur": 67405,
      "usd": 73738,      
      "sats": 105823579
    },
    "ath_change_percentage": {
      "eth": -96.72547,
      "eur": -4.54383,
      "usd": -5.33399,      
      "sats": -5.51121
    },
    "ath_date": {
      "eth": "2015-10-20T00:00:00.000Z",
      "eur": "2024-03-14T07:10:36.635Z",
      "usd": "2024-03-14T07:10:36.635Z",      
      "sats": "2021-05-19T16:00:11.072Z"
    },
    "atl": {
      "eth": 6.779735,
      "eur": 51.3,
      "usd": 67.81,
      "sats": 95099268
    },
    "atl_change_percentage": {
      "eth": 201.48197,
      "eur": 125328.10816,
      "usd": 102843.21661,      
      "sats": 5.14426
    },
    "atl_date": {
      "eth": "2017-06-12T00:00:00.000Z",
      "eur": "2013-07-05T00:00:00.000Z",
      "usd": "2013-07-06T00:00:00.000Z",      
      "sats": "2021-05-19T13:14:13.071Z"
    },
    "market_cap": {
      "eth": 402191019,
      "eur": 1266067979162,
      "usd": 1373546629363,      
      "sats": 1967528713437466
    },
    "market_cap_rank": 1,
    "fully_diluted_valuation": {
      "eth": 429255322,
      "eur": 1351264429277,
      "usd": 1465975550097,      
      "sats": 2099927972120845
    },
    "market_cap_fdv_ratio": 0.94,
    "total_volume": {
      "btc": 270165,
      "eth": 5522486,
      "eur": 17390869691,
      "usd": 18867210007,      
      "sats": 27016488930277
    },
    "high_24h": {
      "eth": 20.57645,
      "eur": 64343,
      "usd": 69805,      
      "sats": 100078943
    },
    "low_24h": {
      "eth": 20.329576,
      "eur": 62695,
      "usd": 67985,      
      "sats": 99830137
    },
    "price_change_24h": 1619,
    "price_change_percentage_24h": 2.37311,
    "price_change_percentage_7d": -0.89706,
    "price_change_percentage_14d": 6.36178,
    "price_change_percentage_30d": 1.81171,
    "price_change_percentage_60d": 62.54292,
    "price_change_percentage_200d": 157.51875,
    "price_change_percentage_1y": 149.76989,
    "market_cap_change_24h": 31172487848,
    "market_cap_change_percentage_24h": 2.32219,
    "price_change_24h_in_currency": {      
      "sats": -22459.0808065832
    },
    "price_change_percentage_1h_in_currency": {
      "sats": 0.01957
    },
    "price_change_percentage_24h_in_currency": {
      "sats": -0.02245
    },
    "price_change_percentage_7d_in_currency": {
      "sats": 0.02215
    },
    "price_change_percentage_14d_in_currency": {
      "sats": -0.02852
    },
    "price_change_percentage_30d_in_currency": {
      "sats": -0.02265
    },
    "price_change_percentage_60d_in_currency": {
      "sats": 0.13558
    },
    "price_change_percentage_200d_in_currency": {
      "sats": 0.02219
    },
    "price_change_percentage_1y_in_currency": {
      "sats": -0.01588
    },
    "market_cap_change_24h_in_currency": {
      "sats": -979813688914.25
    },
    "market_cap_change_percentage_24h_in_currency": {
      "sats": -0.04977
    },
    "total_supply": 21000000,
    "max_supply": 21000000,
    "circulating_supply": 19675962,
    "last_updated": "2024-04-07T15:24:51.021Z"
  },
  "community_data": {
    "facebook_likes": null,
    "reddit_average_posts_48h": 0,
    "reddit_average_comments_48h": 0,
    "reddit_subscribers": 0,
    "reddit_accounts_active_48h": 0,
    "telegram_channel_user_count": null
  },
  "developer_data": {
    "forks": 36426,
    "stars": 73168,
    "subscribers": 3967,
    "total_issues": 7743,
    "closed_issues": 7380,
    "pull_requests_merged": 11215,
    "pull_request_contributors": 846,
    "code_additions_deletions_4_weeks": {
      "additions": 1570,
      "deletions": -1948
    },
    "commit_count_4_weeks": 108,
    "last_4_weeks_commit_activity_series": []
  },
  "status_updates": [],
  "last_updated": "2024-04-07T15:24:51.021Z",
  "tickers": [
    {
      "base": "BTC",
      "target": "USDT",
      "market": {
        "name": "Binance",
        "identifier": "binance",
        "has_trading_incentive": false
      },
      "last": 69816,
      "volume": 19988.82111,
      "converted_last": {
        "btc": 0.99999255,
        "eth": 20.441016,
        "usd": 69835
      },
      "converted_volume": {
        "btc": 19783,
        "eth": 404380,
        "usd": 1381537193
      },
      "trust_score": "green",
      "bid_ask_spread_percentage": 0.010014,
      "timestamp": "2024-04-07T15:23:02+00:00",
      "last_traded_at": "2024-04-07T15:23:02+00:00",
      "last_fetch_at": "2024-04-07T15:24:00+00:00",
      "is_anomaly": false,
      "is_stale": false,
      "trade_url": "https://www.binance.com/en/trade/BTC_USDT?ref=37754157",
      "token_info_url": null,
      "coin_id": "bitcoin",
      "target_coin_id": "tether"
    }
  ]
}
*/

object CoingeckoJson extends DefaultJsonProtocol {
  implicit val js_Token = jsonFormat6(Token)
  implicit val js_Coin = jsonFormat7(Coin)
  implicit val js_cg_Coin = jsonFormat3(Coingecko_Coin)

// JSON formats for the new case classes
  implicit val js_cg_Roi = jsonFormat3(Coingecko_Roi)  
  implicit val js_cg_Image = jsonFormat3(Coingecko_Image)
  implicit val js_cg_Platform = jsonFormat2(Coingecko_Platform)  
  implicit val js_cg_Links = jsonFormat13(Coingecko_Links)
  implicit val js_cg_Description = jsonFormat2(Coingecko_Description)
  implicit val js_cg_Localization = jsonFormat2(Coingecko_Localization)
  implicit val js_cg_MarketData: RootJsonFormat[Coingecko_MarketData] = new RootJsonFormat[Coingecko_MarketData] {
    def write(obj: Coingecko_MarketData): JsValue = JsObject(
      "current_price" -> obj.current_price.toJson,
      "total_value_locked" -> obj.total_value_locked.toJson,
      "mcap_to_tvl_ratio" -> obj.mcap_to_tvl_ratio.toJson,
      "fdv_to_tvl_ratio" -> obj.fdv_to_tvl_ratio.toJson,
      "roi" -> obj.roi.toJson,
      "ath" -> obj.ath.toJson,
      "ath_change_percentage" -> obj.ath_change_percentage.toJson,
      "ath_date" -> obj.ath_date.toJson,
      "atl" -> obj.atl.toJson,
      "atl_change_percentage" -> obj.atl_change_percentage.toJson,
      "atl_date" -> obj.atl_date.toJson,
      "market_cap" -> obj.market_cap.toJson,
      "market_cap_rank" -> JsNumber(obj.market_cap_rank),
      "fully_diluted_valuation" -> obj.fully_diluted_valuation.toJson,
      "market_cap_fdv_ratio" -> obj.market_cap_fdv_ratio.toJson,
      "total_volume" -> obj.total_volume.toJson,
      "high_24h" -> obj.high_24h.toJson,
      "low_24h" -> obj.low_24h.toJson,
      "price_change_24h" -> JsNumber(obj.price_change_24h),
      "price_change_percentage_24h" -> JsNumber(obj.price_change_percentage_24h),
      "price_change_percentage_7d" -> JsNumber(obj.price_change_percentage_7d),
      "price_change_percentage_14d" -> JsNumber(obj.price_change_percentage_14d),
      "price_change_percentage_30d" -> JsNumber(obj.price_change_percentage_30d),
      "price_change_percentage_60d" -> JsNumber(obj.price_change_percentage_60d),
      "price_change_percentage_200d" -> JsNumber(obj.price_change_percentage_200d),
      "price_change_percentage_1y" -> JsNumber(obj.price_change_percentage_1y),
      "market_cap_change_24h" -> JsNumber(obj.market_cap_change_24h),
      "market_cap_change_percentage_24h" -> JsNumber(obj.market_cap_change_percentage_24h),
      "price_change_24h_in_currency" -> obj.price_change_24h_in_currency.toJson,
      "price_change_percentage_1h_in_currency" -> obj.price_change_percentage_1h_in_currency.toJson,
      "price_change_percentage_24h_in_currency" -> obj.price_change_percentage_24h_in_currency.toJson,
      "price_change_percentage_7d_in_currency" -> obj.price_change_percentage_7d_in_currency.toJson,
      "price_change_percentage_14d_in_currency" -> obj.price_change_percentage_14d_in_currency.toJson,
      "price_change_percentage_30d_in_currency" -> obj.price_change_percentage_30d_in_currency.toJson,
      "price_change_percentage_60d_in_currency" -> obj.price_change_percentage_60d_in_currency.toJson,
      "price_change_percentage_200d_in_currency" -> obj.price_change_percentage_200d_in_currency.toJson,
      "price_change_percentage_1y_in_currency" -> obj.price_change_percentage_1y_in_currency.toJson,
      "market_cap_change_24h_in_currency" -> obj.market_cap_change_24h_in_currency.toJson,
      "market_cap_change_percentage_24h_in_currency" -> obj.market_cap_change_percentage_24h_in_currency.toJson,
      "total_supply" -> obj.total_supply.toJson,
      "max_supply" -> obj.max_supply.toJson,
      "max_supply_infinite" -> obj.max_supply_infinite.toJson,
      "circulating_supply" -> obj.circulating_supply.toJson,
      "last_updated" -> JsString(obj.last_updated)
    )
    
    def read(json: JsValue): Coingecko_MarketData = {
      val obj = json.asJsObject.fields
      Coingecko_MarketData(
        current_price = obj("current_price").convertTo[Map[String, Double]],
        total_value_locked = obj.get("total_value_locked").flatMap(_.convertTo[Option[Double]]),
        mcap_to_tvl_ratio = obj.get("mcap_to_tvl_ratio").flatMap(_.convertTo[Option[Double]]),
        fdv_to_tvl_ratio = obj.get("fdv_to_tvl_ratio").flatMap(_.convertTo[Option[Double]]),
        roi = obj.get("roi").flatMap(_.convertTo[Option[Coingecko_Roi]]),
        ath = obj("ath").convertTo[Map[String, Double]],
        ath_change_percentage = obj("ath_change_percentage").convertTo[Map[String, Double]],
        ath_date = obj("ath_date").convertTo[Map[String, String]],
        atl = obj("atl").convertTo[Map[String, Double]],
        atl_change_percentage = obj("atl_change_percentage").convertTo[Map[String, Double]],
        atl_date = obj("atl_date").convertTo[Map[String, String]],
        market_cap = obj("market_cap").convertTo[Map[String, Double]],
        market_cap_rank = obj("market_cap_rank").convertTo[Int],
        fully_diluted_valuation = obj("fully_diluted_valuation").convertTo[Map[String, Double]],
        market_cap_fdv_ratio = obj.get("market_cap_fdv_ratio").flatMap(_.convertTo[Option[Double]]),
        total_volume = obj("total_volume").convertTo[Map[String, Double]],
        high_24h = obj("high_24h").convertTo[Map[String, Double]],
        low_24h = obj("low_24h").convertTo[Map[String, Double]],
        price_change_24h = obj("price_change_24h").convertTo[Double],
        price_change_percentage_24h = obj("price_change_percentage_24h").convertTo[Double],
        price_change_percentage_7d = obj("price_change_percentage_7d").convertTo[Double],
        price_change_percentage_14d = obj("price_change_percentage_14d").convertTo[Double],
        price_change_percentage_30d = obj("price_change_percentage_30d").convertTo[Double],
        price_change_percentage_60d = obj("price_change_percentage_60d").convertTo[Double],
        price_change_percentage_200d = obj("price_change_percentage_200d").convertTo[Double],
        price_change_percentage_1y = obj("price_change_percentage_1y").convertTo[Double],
        market_cap_change_24h = obj("market_cap_change_24h").convertTo[Double],
        market_cap_change_percentage_24h = obj("market_cap_change_percentage_24h").convertTo[Double],
        price_change_24h_in_currency = obj("price_change_24h_in_currency").convertTo[Map[String, Double]],
        price_change_percentage_1h_in_currency = obj("price_change_percentage_1h_in_currency").convertTo[Map[String, Double]],
        price_change_percentage_24h_in_currency = obj("price_change_percentage_24h_in_currency").convertTo[Map[String, Double]],
        price_change_percentage_7d_in_currency = obj("price_change_percentage_7d_in_currency").convertTo[Map[String, Double]],
        price_change_percentage_14d_in_currency = obj("price_change_percentage_14d_in_currency").convertTo[Map[String, Double]],
        price_change_percentage_30d_in_currency = obj("price_change_percentage_30d_in_currency").convertTo[Map[String, Double]],
        price_change_percentage_60d_in_currency = obj("price_change_percentage_60d_in_currency").convertTo[Map[String, Double]],
        price_change_percentage_200d_in_currency = obj("price_change_percentage_200d_in_currency").convertTo[Map[String, Double]],
        price_change_percentage_1y_in_currency = obj("price_change_percentage_1y_in_currency").convertTo[Map[String, Double]],
        market_cap_change_24h_in_currency = obj("market_cap_change_24h_in_currency").convertTo[Map[String, Double]],
        market_cap_change_percentage_24h_in_currency = obj("market_cap_change_percentage_24h_in_currency").convertTo[Map[String, Double]],
        total_supply = obj.get("total_supply").flatMap(_.convertTo[Option[Double]]),
        max_supply = obj.get("max_supply").flatMap(_.convertTo[Option[Double]]),
        max_supply_infinite = obj.get("max_supply_infinite").flatMap(_.convertTo[Option[Boolean]]),
        circulating_supply = obj.get("circulating_supply").flatMap(_.convertTo[Option[Double]]),
        last_updated = obj("last_updated").convertTo[String]
      )
    }
  }
  
  implicit val js_cg_CommunityData = jsonFormat6(Coingecko_CommunityData)
  implicit val js_cg_DeveloperData = jsonFormat10(Coingecko_DeveloperData)
  implicit val js_cg_Market = jsonFormat3(Coingecko_Market)
  implicit val js_cg_Ticker = jsonFormat18(Coingecko_Ticker)
  
  // Custom JSON format for the main CoinData class
  implicit val js_cg_CoinData: RootJsonFormat[Coingecko_CoinData] = new RootJsonFormat[Coingecko_CoinData] {
    def write(obj: Coingecko_CoinData): JsValue = JsObject(
      "id" -> JsString(obj.id),
      "symbol" -> JsString(obj.symbol),
      "name" -> JsString(obj.name),
      "web_slug" -> obj.web_slug.toJson,
      "asset_platform_id" -> obj.asset_platform_id.toJson,
      "platforms" -> obj.platforms.toJson,
      "detail_platforms" -> obj.detail_platforms.toJson,
      "block_time_in_minutes" -> obj.block_time_in_minutes.toJson,
      "hashing_algorithm" -> obj.hashing_algorithm.toJson,
      "categories" -> obj.categories.toJson,
      "preview_listing" -> JsBoolean(obj.preview_listing),
      "public_notice" -> obj.public_notice.toJson,
      "additional_notices" -> obj.additional_notices.toJson,
      "localization" -> (if (obj.localization != null) obj.localization.toJson else JsNull),
      "description" -> (if (obj.description != null) obj.description.toJson else JsNull),
      "links" -> (if (obj.links != null) obj.links.toJson else JsNull),
      "image" -> (if (obj.image != null) obj.image.toJson else JsNull),
      "country_origin" -> JsString(obj.country_origin),
      "genesis_date" -> obj.genesis_date.toJson,
      "sentiment_votes_up_percentage" -> obj.sentiment_votes_up_percentage.toJson,
      "sentiment_votes_down_percentage" -> obj.sentiment_votes_down_percentage.toJson,
      "watchlist_portfolio_users" -> JsNumber(obj.watchlist_portfolio_users),
      "market_cap_rank" -> JsNumber(obj.market_cap_rank),
      "market_data" -> (if (obj.market_data != null) obj.market_data.toJson else JsNull),
      "community_data" -> (if (obj.community_data != null) obj.community_data.toJson else JsNull),
      "developer_data" -> (if (obj.developer_data != null) obj.developer_data.toJson else JsNull),
      "status_updates" -> obj.status_updates.toJson,
      "last_updated" -> JsString(obj.last_updated),
      "tickers" -> obj.tickers.toJson
    )
    
    def read(json: JsValue): Coingecko_CoinData = {
      val obj = json.asJsObject.fields
      Coingecko_CoinData(
        id = obj("id").convertTo[String],
        symbol = obj("symbol").convertTo[String],
        name = obj("name").convertTo[String],
        web_slug = obj.get("web_slug").flatMap(_.convertTo[Option[String]]),
        asset_platform_id = obj.get("asset_platform_id").flatMap(_.convertTo[Option[String]]),
        platforms = obj.get("platforms").flatMap(_.convertTo[Option[Map[String, String]]]),
        detail_platforms = obj("detail_platforms").convertTo[Map[String, Coingecko_Platform]],
        block_time_in_minutes = obj.get("block_time_in_minutes").flatMap(_.convertTo[Option[Int]]),
        hashing_algorithm = obj.get("hashing_algorithm").flatMap(_.convertTo[Option[String]]),
        categories = obj("categories").convertTo[Seq[String]],
        preview_listing = obj("preview_listing").convertTo[Boolean],
        public_notice = obj.get("public_notice").flatMap(_.convertTo[Option[String]]),
        additional_notices = obj("additional_notices").convertTo[Seq[String]],
        localization = if (obj("localization") == JsNull) null else obj("localization").convertTo[Coingecko_Localization],
        description = if (obj("description") == JsNull) null else obj("description").convertTo[Coingecko_Description],
        links = if (obj("links") == JsNull) null else obj("links").convertTo[Coingecko_Links],
        image = if (obj("image") == JsNull) null else obj("image").convertTo[Coingecko_Image],
        country_origin = obj("country_origin").convertTo[String],
        genesis_date = obj.get("genesis_date").flatMap(_.convertTo[Option[String]]),
        sentiment_votes_up_percentage = obj.get("sentiment_votes_up_percentage").flatMap(_.convertTo[Option[Double]]),
        sentiment_votes_down_percentage = obj.get("sentiment_votes_down_percentage").flatMap(_.convertTo[Option[Double]]),
        watchlist_portfolio_users = obj("watchlist_portfolio_users").convertTo[Int],
        market_cap_rank = obj("market_cap_rank").convertTo[Int],
        market_data = if (obj("market_data") == JsNull) null else obj("market_data").convertTo[Coingecko_MarketData],
        community_data = if (obj("community_data") == JsNull) null else obj("community_data").convertTo[Coingecko_CommunityData],
        developer_data = if (obj("developer_data") == JsNull) null else obj("developer_data").convertTo[Coingecko_DeveloperData],
        status_updates = obj("status_updates").convertTo[Seq[String]],
        last_updated = obj("last_updated").convertTo[String],
        tickers = obj("tickers").convertTo[Seq[Coingecko_Ticker]]
      )
    }
  }
}

class Coingecko(uri:String)(implicit ec: ExecutionContext) extends CoingeckoClient {
  import CoingeckoJson._

  val cgUri = CoingeckoURI(uri)
  val tokenProvider = new TokenProviderCoinGecko(Some(cgUri.apiKey))
  
  def getUri() = cgUri

  def requestCoins(ids:Set[String])(implicit ec: ExecutionContext):Future[String] = {
    requestCoins(getUri(),ids,cgUri.apiKey)
    .map(_.utf8String)
  }

  def coins(ids:Set[String])(implicit ec: ExecutionContext):Future[JsValue] = {
    val req = requestCoins(getUri(),ids,cgUri.apiKey)

    req.map(body => {
      val json = body.utf8String.parseJson
      log.debug(s"${ids}: results=${json}")

      json
    })
  }

  def askCoinsAsync(ids:Set[String])(implicit ec: ExecutionContext):Future[JsValue] = {
    coins(ids)
  }

  def askCoinsAsync()(implicit ec: ExecutionContext):Future[JsValue] = {
    coins(Set())
  }

  def askCoins(ids:Set[String])(implicit ec: ExecutionContext):JsValue = {
    val f = coins(ids)
    Await.result(f,cgUri.timeout)
  }

  def askCoins()(implicit ec: ExecutionContext):JsValue = {
    val f = coins(Set())
    Await.result(f,cgUri.timeout)
  }

  def flowCoin = Flow[ByteString]
    .map{ case(body) => {
      log.debug(s"body='${body}'")
      tokenProvider.decodeCoin(body.utf8String)
    }}
    .collect{ case Success(c) => c.toJson }
  
  def flowCoinList = Flow[ByteString]
    .map{ case(body) => {
      log.debug(s"body='${body}'")
      // extract ids
      val cc = body.utf8String.parseJson.convertTo[Seq[Coingecko_Coin]]
      cc.map(_.id)
    }}
    .mapConcat(identity)
    .throttle(1,FiniteDuration(cgUri.throttle,TimeUnit.MILLISECONDS))
    .mapAsync(1)(id => {
      requestCoins(getUri(),Set(id),cgUri.apiKey)
    })    
    .via(flowCoin)

  def flowCoins(ids:Set[String],
             freq:Long = 10000L,  
             frameDelimiter:String = "\n",frameSize:Int = 1024 * 1024):Source[ByteString,_] = {

    // Frequency source
    val s0 = Source
      .tick(FiniteDuration(250L,TimeUnit.MILLISECONDS),FiniteDuration(freq,TimeUnit.MILLISECONDS),() => ids)
      .mapAsync(1)(fun => {
        val ids = fun()
        requestCoins(getUri(),ids,cgUri.apiKey)
      })
      .via(flowCoin)
      .map(json => ByteString(json.toString))

    s0    
  }

  def source(ids:Set[String],
             frameDelimiter:String = "\n",frameSize:Int = 1024 * 1024):Source[ByteString,_] = {
    
    Source
      .single(ids)
      .mapAsync(1)(ids => {
        requestCoins(getUri(),ids,cgUri.apiKey)
      })
      // need to have a default throttle here
      .throttle(1,FiniteDuration(cgUri.throttle,TimeUnit.MILLISECONDS))
  }
}

object Coingecko {
  val log = Logger[Coingecko]

  def fromCoingecko(uri:String)(implicit ec: ExecutionContext):(Coingecko,Source[ByteString,_]) = {
    val cg = new Coingecko(uri)(ec)
    (cg,cg.source(cg.cgUri.ops.get("ids").map(_.split(",").toSet).getOrElse(Set())))
  }

  def apply(uri:String)(implicit ec: ExecutionContext):Try[Coingecko] = {
    try {
      val client = new Coingecko(uri)(ec)
      Success(client)

    } catch {
      case e:Exception => return Failure(e)
    }
  }

  def parseCoinData(json:String,parser:String)(implicit fmt:JsonFormat[Coingecko_CoinData]):Option[Coingecko_CoinData] = {
    try {
      parser match {
        case "none" => 
          None
        case "ujson" =>
          val u = ujson.read(json)
          
          // Extract basic fields
          val id = u.obj.get("id").map(_.str).getOrElse("")
          val name = u.obj.get("name").map(_.str).getOrElse("")
          val symbol = u.obj.get("symbol").map(_.str).getOrElse("")
          
          // Extract optional fields with defaults          
          val assetPlatformId = u.obj("asset_platform_id").strOpt
          val platforms = u.obj.get("platforms").map(_.obj.map { case (k, v) => k -> v.str }.toMap)
          
          val lastUpdated = u.obj.get("last_updated").map(_.str).getOrElse("")
                              
          val image = Coingecko_Image(
            thumb = u.obj.get("image").flatMap(_.obj.get("thumb")).map(_.str).getOrElse(""),
            small = u.obj.get("image").flatMap(_.obj.get("small")).map(_.str).getOrElse(""),
            large = u.obj.get("image").flatMap(_.obj.get("large")).map(_.str).getOrElse("")
          )
          
          // Create detail platforms map
          val detailPlatforms = u.obj.get("detail_platforms").map(_.obj.map { case (k, v) =>
            k -> Coingecko_Platform(
              decimal_place = v.obj.get("decimal_place").numOpt.map(_.toInt),
              contract_address = v.obj.get("contract_address").map(_.str).getOrElse("")
            )
          }.toMap).getOrElse(Map.empty)
          
          val c = Coingecko_CoinData(
            id = id,
            symbol = symbol,
            name = name,
            web_slug = None, // Not extracted
            asset_platform_id = assetPlatformId,
            platforms = platforms,
            detail_platforms = detailPlatforms,
            block_time_in_minutes = None, // Not extracted
            hashing_algorithm = None, // Not extracted
            categories = Seq.empty, // Not extracted
            preview_listing = false, // Not extracted
            public_notice = None, // Not extracted
            additional_notices = Seq.empty, // Not extracted
            localization = null, // Not needed
            description = null, // Not needed
            links = null, // Not needed
            image = image,
            country_origin = "", // Not extracted
            genesis_date = None, // Not extracted
            sentiment_votes_up_percentage = None, // Not extracted
            sentiment_votes_down_percentage = None, // Not extracted
            watchlist_portfolio_users = 0, // Not extracted
            market_cap_rank = 0, // Not extracted
            market_data = null, // Not needed
            community_data = null, // Not needed
            developer_data = null, // Not needed
            status_updates = Seq.empty, // Not extracted
            last_updated = lastUpdated,
            tickers = Seq.empty // Not extracted
          )

          Some(c)
        case "json" =>
          Some(json.parseJson.convertTo[Coingecko_CoinData])
      }
    } catch {
      case e: Exception =>
        log.warn(s"failed to parse: id=${Coingecko.getRawId(json)}: '${Util.trunc(json,64)}': ${e.getMessage}",e)
        None
    }
  }

  def getRawId(json:String):Option[String] = {
    Util.walkJson(json,".id").getOrElse(Seq()).headOption.map(_.toString)
  }

  def toBlockchain(chain:String):String = {
    chain match {
      case "polygon-pos" => Blockchain.POLYGON_MAINNET.name
      case _ => chain
    }
  }

  def toCoin(c:Coingecko_CoinData):Coin = {
    val tokens:Map[String,Token] = if(! c.platforms.isDefined) 
      Map.empty 
    else {
      c.platforms.get.flatMap{ case(chain,addr) => 
        if(addr.isBlank()) 
          None 
        else {
          val bid = toBlockchain(chain)
          val dec = c.detail_platforms(chain).decimal_place.getOrElse(18)
          Some(Token(
            bid = bid,
            sym = c.symbol,
            addr = addr,              
            dec = dec,              
          ))
        }
      }
      .map(t => t.bid -> t)
      .toMap
    }

    Coin(
      sym = c.symbol,         
      tokens = tokens, 
      icon = Some(c.image.large),
      id = Some(c.id),
      sid = Some("cg"),
      xid = Some(c.id),
    )
  }
}