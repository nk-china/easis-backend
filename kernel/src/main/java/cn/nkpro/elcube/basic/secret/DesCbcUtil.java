/*
 * This file is part of ELCube.
 *
 * ELCube is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ELCube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with ELCube.  If not, see <https://www.gnu.org/licenses/>.
 */
package cn.nkpro.elcube.basic.secret;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Base64Utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.security.Key;

@Slf4j
public class DesCbcUtil {
    // 加解密统一使用的编码方式
    private final static String encoding = "UTF-8";

    /**
     * 3DES加密
     *
     * @param plainText 普通文本
     * @return 加密后的文本，失败返回null
     */
    public static String encode(String plainText, String secretKey, String iv) {
        String result = null;
        try {
            DESedeKeySpec deSedeKeySpec = new DESedeKeySpec(secretKey.getBytes());
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("desede");
            Key desKey = secretKeyFactory.generateSecret(deSedeKeySpec);
            Cipher cipher = Cipher.getInstance("desede/CBC/PKCS5Padding");
            IvParameterSpec ips = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.ENCRYPT_MODE, desKey, ips);
            byte[] encryptData = cipher.doFinal(plainText.getBytes(encoding));
            result = Base64Utils.encodeToString(encryptData);
        } catch (Exception e) {
            log.error("DesCbcUtil encode error : {}", e.getMessage());
        }
        return result;
    }

    /**
     * 3DES解密
     *
     * @param encryptText 加密文本
     * @return 解密后明文，失败返回null
     */
    public static String decode(String encryptText, String secretKey, String iv) {
        String result = null;
        try {
            DESedeKeySpec spec = new DESedeKeySpec(secretKey.getBytes());
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("desede");
            Key desKey = secretKeyFactory.generateSecret(spec);
            Cipher cipher = Cipher.getInstance("desede/CBC/PKCS5Padding");
            IvParameterSpec ips = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, desKey, ips);
            byte[] decryptData = cipher.doFinal(Base64Utils.decodeFromString(encryptText));
            result = new String(decryptData, encoding);
        } catch (Exception e) {
            log.error("DesCbcUtil decode error : {}", e.getMessage());
        }
        return result;
    }

}